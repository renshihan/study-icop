package org.coody.framework.box.init;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.coody.framework.box.annotation.Around;
import org.coody.framework.box.annotation.CronTask;
import org.coody.framework.box.annotation.InitBean;
import org.coody.framework.box.annotation.OutBean;
import org.coody.framework.box.annotation.PathBinding;
import org.coody.framework.box.aspect.entity.AspectEntity;
import org.coody.framework.box.constant.BoxConstant;
import org.coody.framework.box.container.BeanContainer;
import org.coody.framework.box.container.BuiltContainer;
import org.coody.framework.box.container.MappingContainer;
import org.coody.framework.box.exception.BeanNotFoundException;
import org.coody.framework.box.exception.ErrorCronException;
import org.coody.framework.box.iface.InitFace;
import org.coody.framework.box.proxy.CglibProxy;
import org.coody.framework.box.task.TaskTrigger;
import org.coody.framework.util.AspectUtil;
import org.coody.framework.util.ClassUtil;
import org.coody.framework.util.PrintException;
import org.coody.framework.util.PropertUtil;
import org.coody.framework.util.StringUtil;
@Slf4j
public class BoxRute {


	static CglibProxy proxy = new CglibProxy();

	/**
	 * 初始化指定packets下的web项目
	 * */
	public static void init(String... packets) throws Exception {

		Set<Class<?>> clazzs = new HashSet<Class<?>>();
        //此文件路径是为了拿到除框架之外的类
		for (String packet : packets) {
			Set<Class<?>> clazzsTemp = ClassUtil.getClasses(packet);
			clazzs.addAll(clazzsTemp);
			log.info("加载指定目录下[{}]加载类数量----{}",packet,clazzsTemp.size());
		}
		if (StringUtil.isNullOrEmpty(clazzs)) {
			log.info("加载指定目录加载类为空");
			return;
		}

		for (Class<?> clazz : BuiltContainer.initAspect) {
			log.info("加载切面服务[{}]....",clazz.getName());
			clazzs.add(clazz);
		}
		log.info("##################################");
		log.info("开始初始化功能....{}.",clazzs.size());       //一共在这几个类里找

		initAspect(clazzs);
		initTask(clazzs);
		initClass(clazzs);
		initField();
		initMvc(clazzs);
		initRun(clazzs);

	}

	public static void initTask(Set<Class<?>> clas) {
		log.info("初始化定时任务....");
		if (StringUtil.isNullOrEmpty(clas)) {
			return;
		}
		for (Class<?> cla : clas) {
			InitBean initBean = cla.getAnnotation(InitBean.class);
			if (StringUtil.isNullOrEmpty(initBean)) {
				continue;
			}
			Method[] methods = cla.getDeclaredMethods();
			if (StringUtil.isNullOrEmpty(methods)) {
				continue;
			}
			for (Method method : methods) {
				CronTask cronTask = method.getAnnotation(CronTask.class);
				if (StringUtil.isNullOrEmpty(cronTask) || StringUtil.isNullOrEmpty(cronTask.value())) {
					continue;
				}
				AspectEntity aspectEntity=new AspectEntity();
				//装载切面控制方法
				aspectEntity.setAnnotationClass(new Class<?>[]{CronTask.class});
				aspectEntity.setAspectInvokeMethod(TaskTrigger.getTriggerMethod());


				//将定时任务管理方法当切点方法放入切面容器中
				BoxConstant.aspectMap.put(AspectUtil.getBeanKey(TaskTrigger.getTriggerMethod()), aspectEntity);
			}
		}
	}
	//只加载有注解 InitBean---->Around的类
	public static void initAspect(Set<Class<?>> clas) {
		log.info("初始化切面....");
		if (StringUtil.isNullOrEmpty(clas)) {
			return;
		}
		for (Class<?> cla : clas) {
			InitBean initBean = cla.getAnnotation(InitBean.class);
			if (StringUtil.isNullOrEmpty(initBean)) {
				continue;
			}
			Method[] methods = cla.getDeclaredMethods();
			if (StringUtil.isNullOrEmpty(methods)) {
				continue;
			}
			for (Method method : methods) {
				Around[] arounds = method.getAnnotationsByType(Around.class);
				if(StringUtil.isNullOrEmpty(arounds)){
					continue;
				}
				for (Around around : arounds) {
					if (around == null) {
						continue;
					}
					if (StringUtil.isAllNull(around.annotationClass(), around.classMappath(),
							around.methodMappath())) {
						continue;
					}
					AspectEntity aspectEntity=new AspectEntity();
					//装载切面控制方法的实体
					aspectEntity.setAnnotationClass(around.annotationClass());      //什么切面  目前支持三种切面:logger,transcation,cache初始化中的三个切面
					aspectEntity.setMethodMappath(around.methodMappath());
					aspectEntity.setClassMappath(around.classMappath());
					aspectEntity.setAspectInvokeMethod(method);


					//最终将封装好类切面信息的实体类放入切面容器中   ----   key为被切方法的md5值，value为切面信息实体
					BoxConstant.aspectMap.put(AspectUtil.getBeanKey(method), aspectEntity);
				}
			}
		}
	}

	public static void initClass(Set<Class<?>> clas) throws Exception {
		log.info("初始化class类....");
		if (StringUtil.isNullOrEmpty(clas)) {
			return;
		}

		for (Class<?> cla : clas) {
			List<String> beanNames = BeanContainer.getBeanNames(cla);
			if (StringUtil.isNullOrEmpty(beanNames)) {
				continue;
			}
			//cglib动态代理
			Object bean = proxy.getProxy(cla);
			for (String beanName : beanNames) {
				if (StringUtil.isNullOrEmpty(beanName)) {
					continue;
				}
				if (BeanContainer.containsBean(beanName)) {
					log.error("存在重复的bean:" + beanName);
					continue;
				}
				BeanContainer.writeBean(beanName, bean);
			}
		}
	}
	/**
	 * 作用：执行实体类中使用了initFace接口的bean对象（关于实现方法init()）
	 * 			开始初始化执行定时任务
     * 		2.第二次触发定时任务是由于第一次放入线程池后，
	 * */
	public static void initRun(Set<Class<?>> clas) {
		log.info("初始化run...（启动定时任务）.");
		for (Class<?> clazz : clas) {
			InitBean initBean = clazz.getAnnotation(InitBean.class);
			if (initBean == null) {
				continue;
			}
			Object bean = BeanContainer.getBean(clazz);
			//对于实现了initFace的实现类，会在框架启动的时候运行init方法
			if (InitFace.class.isAssignableFrom(bean.getClass())) {
				// 初始化运行
				try {
					InitFace face = (InitFace) bean;
					if (StringUtil.isNullOrEmpty(face)) {
						continue;
					}
					face.init();
				} catch (Exception e) {
					PrintException.printException(log, e);
				}
			}
			// 执行定时任务
			Method[] methods = clazz.getDeclaredMethods();
			if (StringUtil.isNullOrEmpty(methods)) {
				continue;
			}
			for (Method method : methods) {
				CronTask task = method.getAnnotation(CronTask.class);
				if (task == null) {
					continue;
				}
				try {
					if (StringUtil.isNullOrEmpty(task.value())) {
						PrintException.printException(log, new ErrorCronException(
								"CRON有误:" + bean.getClass() + ":" + method.getName() + ",Cron:" + task.value()));
						continue;
					}
					//将bean，方法，task内容组成
					TaskTrigger.nextRun(bean, method, task.value(), null);
				} catch (Exception e) {
					PrintException.printException(log, new ErrorCronException(
							"CRON有误:" + bean.getClass() + ":" + method.getName() + ",Cron:" + task.value()));
					continue;
				}
			}
		}
	}

	//针对被@OutBean注解过成员变量
	public static void initField() throws Exception {
		log.info("初始化bean成员变量.....");
		for (Object bean : BeanContainer.getBeans()) {
			List<Field> fields = loadFields(bean.getClass());
			if (StringUtil.isNullOrEmpty(fields)) {
				continue;
			}
			for (Field field : fields) {
				OutBean writeBean = field.getAnnotation(OutBean.class);
				if (StringUtil.isNullOrEmpty(writeBean)) {
					continue;
				}
				String beanName = writeBean.beanName();
				if (StringUtil.isNullOrEmpty(beanName)) {
					beanName = field.getType().getName();
				}
				if (!BeanContainer.containsBean(beanName)) {
					throw new BeanNotFoundException(
							"注入失败:" + bean.getClass() + ",未找到Bean:" + field.getName() + "(" + field.getType() + ")");
				}
				Object writeValue = null;
				field.setAccessible(true);		//改为true的意思是然我们在反射中访问私有变量
//				log.info("注入bean的beanName----{}",beanName);	//org.coody.web.service.IcopService
				writeValue = BeanContainer.getBean(beanName);	//根据beanName到bean容器中找对应的实例对象，然后再设置回去
				field.set(bean, writeValue);		//将bean的name添加到fld
			}
		}
	}

	public static void initMvc(Set<Class<?>> clazzs) throws Exception {
		log.info("初始化mvc.....");
		for (Class<?> clazz : clazzs) {
			Object bean = BeanContainer.getBean(clazz);
			if (StringUtil.isNullOrEmpty(bean)) {
				continue;
			}
			PathBinding classBindings = clazz.getAnnotation(PathBinding.class);
			if (StringUtil.isNullOrEmpty(classBindings)) {
				continue;
			}
			Method[] methods = clazz.getDeclaredMethods();
			for (String clazzBinding : classBindings.value()) {
				for (Method method : methods) {
					PathBinding methodBinding = method.getAnnotation(PathBinding.class);
					if (StringUtil.isNullOrEmpty(methodBinding)) {
						continue;
					}
					for (String bindingPath : methodBinding.value()) {
						String path = StringUtil.formatPath(clazzBinding + "/" + bindingPath);
						if (MappingContainer.containsPath(path)) {
							log.error("该地址已注册:" + path);
							continue;
						}
						MappingContainer.MvcMapping mapping = new MappingContainer.MvcMapping();
						mapping.setBean(bean);
						mapping.setPath(path);
						mapping.setMethod(method);
						mapping.setParamTypes(PropertUtil.getMethodParas(method));
						MappingContainer.writeMapping(mapping);
					}
				}
			}
		}
	}

	public static List<Field> loadFields(Class<?> clazz) {
		List<Field> fields = new ArrayList<Field>();
		Field[] fieldArgs = clazz.getDeclaredFields();
		for (Field f : fieldArgs) {
			fields.add(f);
		}
		Class<?> superClass = clazz.getSuperclass();
		if (superClass == null) {
			return fields;
		}
		List<Field> childFields = loadFields(superClass);
		if (StringUtil.isNullOrEmpty(childFields)) {
			return fields;
		}
		fields.addAll(childFields);
		return fields;
	}
}

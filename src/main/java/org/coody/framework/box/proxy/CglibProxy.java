package org.coody.framework.box.proxy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.coody.framework.box.aspect.entity.AspectEntity;
import org.coody.framework.box.constant.BoxConstant;
import org.coody.framework.box.container.BeanContainer;
import org.coody.framework.box.point.AspectPoint;
import org.coody.framework.util.AntUtil;
import org.coody.framework.util.AspectUtil;
import org.coody.framework.util.PropertUtil;
import org.coody.framework.util.StringUtil;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

public class CglibProxy implements MethodInterceptor {

	/**
	 * key拦截方法，value拦截器的方法
	 */
	public static final Map<Method, Set<Method>> interceptMap = new ConcurrentHashMap<Method, Set<Method>>();

	public Object getProxy(Class<?> clazz) throws InstantiationException, IllegalAccessException {
		Integer modifier = clazz.getModifiers();	//获取到本类属性的修饰符
		if (Modifier.isAbstract(modifier)) {		//如果是抽象类
			return null;
		}
		if (Modifier.isInterface(modifier)) {		//如果是接口
			return null;
		}
		if (!isNeedProxyMethods(clazz)) {			//如果不需要代理，就实例化
			return clazz.newInstance();
		}
		Enhancer enhancer = new Enhancer();
		// 设置需要创建子类的类
		enhancer.setSuperclass(clazz);
		enhancer.setCallback(this);
		// 通过字节码技术动态创建子类实例
		return enhancer.create();			//如果是需要代理，就需要使用cglib。
	}

	/**
	 * 判断是否需要cglib动态代理的条件：
	 * 1.该类必须有方法
	 * 2.该类必须被几个切面注解注释过（有一个就可以）
	 *
	 * 需要代理的话，将需要拦截的方法，以及对应的切面方法放入拦截容器中
	 * */
	private boolean isNeedProxyMethods(Class<?> clazz) {
		if(StringUtil.isNullOrEmpty(clazz.getDeclaredMethods())){
			return false;
		}
		boolean needProxy=false;
		for(Method method:clazz.getDeclaredMethods()){
			for(AspectEntity aspectEntity:BoxConstant.aspectMap.values()){
				if(!needProxy(clazz, aspectEntity, method)){
					continue;
				}
				needProxy= true;

				if (interceptMap.containsKey(method)) {
					interceptMap.get(method).add(aspectEntity.getAspectInvokeMethod());
				}else{
					Set<Method> aspectMethods = new HashSet<Method>();
					aspectMethods.add(aspectEntity.getAspectInvokeMethod());
					interceptMap.put(method, aspectMethods);
				}
			}
		}
		return needProxy;
	}

	private boolean needProxy(Class<?> clazz,AspectEntity aspectEntity,Method method){
		/**
		 * 判断类名是否满足条件
		 */
		if(!StringUtil.isNullOrEmpty(aspectEntity.getClassMappath())){
			if(!AntUtil.isAntMatch(clazz.getName(), aspectEntity.getClassMappath())){
				return false;
			}
		}
		/**
		 * 判断方法名是否满足条件
		 */
		if(!StringUtil.isNullOrEmpty(aspectEntity.getMethodMappath())){
			if(!AntUtil.isAntMatch(AspectUtil.getMethodUnionKey(method), aspectEntity.getMethodMappath())){
				return false;
			}
		}
		/**
		 * 判断注解是否满足条件
		 */
		if(!StringUtil.isNullOrEmpty(aspectEntity.getAnnotationClass())){
			Annotation[] annotations=method.getAnnotations();
			if(StringUtil.isNullOrEmpty(annotations)){
				return false;
			}
			List<Class<?>> annotationClazzs=new ArrayList<Class<?>>();
			for(Annotation annotation:annotations){
				annotationClazzs.add(annotation.annotationType());
			}
			for(Class<?> aspectAnnotationClazz:aspectEntity.getAnnotationClass()){
				if(!annotationClazzs.contains(aspectAnnotationClazz)){
					return false;
				}
			}
		}
		return true;
	}
	@Override
	// 拦截父类所有方法的调用
	public Object intercept(Object bean, Method method, Object[] params, MethodProxy proxy) throws Throwable {
		//如果拦截容器中没有该方法的切面，
		if (!interceptMap.containsKey(method)) {
			return proxy.invokeSuper(bean, params);
		}
		List<Method> invokeMethods = new ArrayList<Method>(interceptMap.get(method));
		Method invokeMethod = invokeMethods.get(0);
		invokeMethods.remove(0);
		Class<?> clazz = PropertUtil.getClass(invokeMethod);
		Object invokeBean = BeanContainer.getBean(clazz);
		AspectPoint wrapper = new AspectPoint();
		wrapper.setBean(bean);
		wrapper.setMethod(method);
		wrapper.setParams(params);
		wrapper.setProxy(proxy);
		wrapper.setClazz(bean.getClass());
		AspectPoint childWrapper = parseAspect(bean, wrapper, invokeMethods);
		if (childWrapper != null) {
			wrapper.setBean(invokeBean);
			wrapper.setChildAspect(childWrapper);
		}
		return invokeMethod.invoke(invokeBean, wrapper);
	}

	private AspectPoint parseAspect(Object lastBean, AspectPoint baseWrapper, List<Method> invokeMethods) {
		if (StringUtil.isNullOrEmpty(invokeMethods)) {
			return null;
		}
		Method currentAspectMethod = invokeMethods.get(0);
		invokeMethods.remove(0);
		AspectPoint wrapper = new AspectPoint();
		wrapper.setBean(baseWrapper.getBean());
		wrapper.setMethod(baseWrapper.getMethod());
		wrapper.setParams(baseWrapper.getParams());
		wrapper.setProxy(baseWrapper.getProxy());
		wrapper.setClazz(baseWrapper.getClazz());
		wrapper.setCurrentAspectMethod(currentAspectMethod);
		AspectPoint childWrapper = parseAspect(lastBean, baseWrapper, invokeMethods);
		if (childWrapper != null) {
			wrapper.setChildAspect(childWrapper);
			return wrapper;
		}
		wrapper.setBean(lastBean);
		return wrapper;
	}
}

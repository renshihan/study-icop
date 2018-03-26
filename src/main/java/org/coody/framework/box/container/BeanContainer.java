package org.coody.framework.box.container;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.coody.framework.box.annotation.PathBinding;
import org.coody.framework.box.constant.BoxConstant;
import org.coody.framework.util.PropertUtil;
import org.coody.framework.util.StringUtil;
@Slf4j
@SuppressWarnings({"unchecked","rawtypes"})
public class BeanContainer {
	
	
	private static Map<String, Object> beanMap=new ConcurrentHashMap<String, Object>();
	
	public static <T> T getBean(Class<?> cla){
		String beanName=getBeanName(cla);
		if(StringUtil.isNullOrEmpty(beanName)){
			return null;
		}
		return (T) beanMap.get(beanName);
	}
	
	public static <T> T getBean(String beanName){
		if(StringUtil.isNullOrEmpty(beanName)){
			return null;
		}
		return (T) beanMap.get(beanName);
	}
	public static void writeBean(String beanName,Object bean){
		beanMap.put(beanName, bean);
	}
	public static boolean containsBean(String beanName){
		return beanMap.containsKey(beanName);
	}
	public static Collection<?> getBeans(){
		return beanMap.values();
	}
	/**
	 * InitBean.class, PathBinding.class  看是否被这两个注解注释，
	 * 如果类被pathBinding注释过就直接返回类名,
	 * 如果类被IniBean注释过，就获取注释的initBean的value值当做beanName值
	 * */
	public static String getBeanName(Class<?> clazz){
		for (Class annotationClass : BoxConstant.beanAnnotations) {
			Annotation initBean = clazz.getAnnotation(annotationClass);
			if (StringUtil.isNullOrEmpty(initBean)) {
				continue;
			}
			String beanName = clazz.getName();
			if(PathBinding.class.isAssignableFrom(initBean.annotationType())){
				return beanName;
			}
			beanName = (String) PropertUtil.getAnnotationValue(initBean, "value");
			if (StringUtil.isNullOrEmpty(beanName)) {
				return clazz.getName();
			}
		}
		return null;
	}
	/**
	 * 将传入类的类名和其接口类的类名返回
	 * */
	public static List<String> getBeanNames(Class<?> clazz){
		Set<String> beanNames=new HashSet<String>();
		String beanName=getBeanName(clazz);//获取该类的类名添加到beanNames列表中
		log.info("---class:{}----11111111-----{}",clazz.getName(),beanName);
		if(StringUtil.isNullOrEmpty(beanName)){
			return null;
		}
		beanNames.add(beanName);
		Class<?>[] clazzs=clazz.getInterfaces();	///获取该类的接口
		if(!StringUtil.isNullOrEmpty(clazzs)){
			for(Class<?> clazTemp:clazzs){
				beanName=getBeanName(clazTemp);		//如果有接口，获取到该类接口的名称
				if(StringUtil.isNullOrEmpty(beanName)){
					beanName=clazTemp.getName();
				}
				if(BeanContainer.containsBean(beanName)){	//如果该类名在beanContainer已经存在，就跳过，否则在beanNames容器中添加
					continue;
				}
				beanNames.add(beanName);
			}
		}
		if(StringUtil.isNullOrEmpty(beanNames)){
			return null;
		}
		return new ArrayList<String>(beanNames);
	}
}

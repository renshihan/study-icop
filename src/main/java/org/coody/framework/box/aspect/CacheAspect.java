package org.coody.framework.box.aspect;

import java.lang.reflect.Method;

import lombok.extern.slf4j.Slf4j;
import org.coody.framework.box.annotation.Around;
import org.coody.framework.box.annotation.CacheWipe;
import org.coody.framework.box.annotation.CacheWipes;
import org.coody.framework.box.annotation.CacheWrite;
import org.coody.framework.box.annotation.InitBean;
import org.coody.framework.box.cache.LocalCache;
import org.coody.framework.box.point.AspectPoint;
import org.coody.framework.util.AspectUtil;
import org.coody.framework.util.StringUtil;

@InitBean
@Slf4j
public class CacheAspect {

	/**
	 * 写缓存操作
	 * 
	 * @param aspect
	 * @return
	 * @throws Throwable
	 */
	@Around(annotationClass=CacheWrite.class)
	public Object cCacheWrite(AspectPoint aspect) throws Throwable {
		Class<?> clazz = aspect.getClazz();
		Method method = aspect.getMethod();
		if (method == null) {
			return aspect.invoke();
		}
		// 获取注解
		CacheWrite handle = method.getAnnotation(CacheWrite.class);
		if (handle == null) {
			return aspect.invoke();
		}
		// 封装缓存KEY
		Object[] paras = aspect.getParams();
		String key = handle.key();
		try {
			if (StringUtil.isNullOrEmpty(key)) {
				key = AspectUtil.getMethodKey(clazz, method);
			}
			if (StringUtil.isNullOrEmpty(handle.fields())) {
				String paraKey = AspectUtil.getBeanKey(paras);
				if (!StringUtil.isNullOrEmpty(paraKey)) {
					key += ":";
					key += paraKey;
				}
			}
			if (!StringUtil.isNullOrEmpty(handle.fields())) {
				key = AspectUtil.getFieldKey(clazz, method, paras, key, handle.fields());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		Integer cacheTimer = ((handle.time() == 0) ? 24 * 3600 : handle.time());
		// 获取缓存
		try {
			Object result = LocalCache.getCache(key);
			log.debug("获取缓存:" + key + ",结果:" + result);
			if (!StringUtil.isNullOrEmpty(result)) {
				return result;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		Object result = aspect.invoke();
		if (result != null) {
			try {
				LocalCache.setCache(key, result, cacheTimer);
				log.debug("设置缓存:" + key + ",结果:" + result + ",缓存时间:" + cacheTimer);
			} catch (Exception e) {
			}
		}
		return result;
	}

	/**
	 * 缓存清理
	 * 
	 * @param aspect
	 * @return
	 * @throws Throwable
	 */
	@Around(annotationClass=CacheWipes.class)
	@Around(annotationClass=CacheWipe.class)
	public Object zCacheWipe(AspectPoint aspect) throws Throwable {
		Class<?> clazz = aspect.getClazz();
		Method method = aspect.getMethod();
		if (method == null) {
			return aspect.invoke();
		}
		Object[] paras = aspect.getParams();
		Object result = aspect.invoke();
		CacheWipe[] handles = method.getAnnotationsByType(CacheWipe.class);
		if (StringUtil.isNullOrEmpty(handles)) {
			return result;
		}
		for (CacheWipe handle : handles) {
			try {
				String key = handle.key();
				if (StringUtil.isNullOrEmpty(handle.key())) {
					key = (AspectUtil.getMethodKey(clazz, method));
				}
				if (!StringUtil.isNullOrEmpty(handle.fields())) {
					key = AspectUtil.getFieldKey(clazz, method, paras, key, handle.fields());
				}
				log.debug("删除缓存:" + key);
				LocalCache.delCache(key);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return result;
	}
}

package org.coody.framework.box.mvc;

import java.io.IOException;
import java.text.MessageFormat;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.coody.framework.box.adapt.ParamsAdapt;
import org.coody.framework.box.annotation.JsonSerialize;
import org.coody.framework.box.container.MappingContainer;
import org.coody.framework.box.container.RequestContainer;
import org.coody.framework.util.StringUtil;

import com.alibaba.fastjson.JSON;

@Slf4j
@SuppressWarnings("serial")
public class DispatServlet extends HttpServlet{
	public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setCharacterEncoding("UTF-8");
		String path=request.getServletPath();
		log.debug("收到请求:"+path);
		if(!MappingContainer.containsPath(path)){
			response.getWriter().print("page not found");
			response.setStatus(404);
			return;
		}
		MappingContainer.MvcMapping mapping=MappingContainer.getMapping(path);
		//装载Request
		RequestContainer.setRequest(request);
		RequestContainer.setResponse(response);
		try {
			Object[] params=ParamsAdapt.adaptParams(mapping.getMethod().getParameterTypes(), null, request, response, request.getSession());
			Object	result=mapping.getMethod().invoke(mapping.getBean(), params);
			if(result==null){
				return;
			}
			JsonSerialize jsonSerialize=mapping.getMethod().getAnnotation(JsonSerialize.class);
			if(jsonSerialize!=null){
				response.setContentType("application/Json");
				String json=JSON.toJSONString(result);
				response.getWriter().print(json);
				return;
			}
			String viewFileName=StringUtil.toString(result);
			if(StringUtil.isNullOrEmpty(viewFileName)){
				response.getWriter().print("page not found");
				response.setStatus(404);
				return;
			}
			String viewPath=getServletConfig().getInitParameter("viewPath");
			String respFile=MessageFormat.format("{0}/{1}", viewPath,viewFileName);
			request.getRequestDispatcher(respFile).forward(request, response);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public void init(){}
}

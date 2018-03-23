package org.coody.framework.box.init;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import lombok.extern.slf4j.Slf4j;
import org.coody.framework.util.StringUtil;

@Slf4j
public class BoxServletListen implements ServletContextListener {
	public void contextDestroyed(ServletContextEvent arg0) {
		System.out.println("运行contextDestroyed");
	}
	/**
	 * servlet监听器负责项目的初始化
	 * 该监听器配置在web.xml中
	 * */
	public void contextInitialized(ServletContextEvent event) {
		try {
			String packet = event.getServletContext().getInitParameter("scanPacket");
			if (StringUtil.isNullOrEmpty(packet)) {
				log.error("启动参数:scanPacket为空");
				return;
			}
			String[] packets = packet.split(",");
			BoxRute.init(packets);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}

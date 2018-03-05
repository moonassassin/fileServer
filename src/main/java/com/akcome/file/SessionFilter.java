package com.akcome.file;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SessionFilter implements Filter {

	@Override
	public void destroy() {

	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) req;
		if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
			chain.doFilter(req, res);
		} else {
			HttpServletResponse httpResponse = (HttpServletResponse) res;
			String token = httpRequest.getHeader("token");
			if (StringUtils.isEmpty(token)) {
				token = httpRequest.getParameter("token");
			}

			if (StringUtils.hasText(token)) {
				Cookie cookie = new Cookie("token", token);
				cookie.setMaxAge(3600);
				cookie.setPath("/");
				httpResponse.addCookie(cookie);
			}
			chain.doFilter(req, res);
		}
	}

	@Override
	public void init(FilterConfig arg0) throws ServletException {

	}

}

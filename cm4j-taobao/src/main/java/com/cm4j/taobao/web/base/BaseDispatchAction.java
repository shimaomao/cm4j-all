package com.cm4j.taobao.web.base;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.WebUtils;

import com.cm4j.taobao.api.common.APICaller;
import com.cm4j.taobao.api.common.APIConstants;
import com.cm4j.taobao.web.login.UserSession;
import com.taobao.api.ApiException;
import com.taobao.api.TaobaoRequest;
import com.taobao.api.TaobaoResponse;

public class BaseDispatchAction {

	public static final Logger logger = LoggerFactory.getLogger(BaseDispatchAction.class);

	/**
	 * 错误页面
	 */
	public static String ERROR_PAGE = "/error";
	/**
	 * 放到request的消息key
	 */
	public static String MESSAGE_KEY = "msg";
	/**
	 * 成功提示页面
	 */
	public static String SUCCESS_PAGE = "/success";

	/**
	 * 获取request
	 * 
	 * @return
	 */
	protected HttpServletRequest getRequest() {
		return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
	}

	/**
	 * 添加错误信息到request
	 * 
	 * @param obj
	 */
	protected void addRequestErrorMessage(Object obj) {
		getRequest().setAttribute(MESSAGE_KEY, obj);
	}

	/**
	 * 获取应用的登陆地址
	 * 
	 * @return
	 */
	public static String getLoginUrl() {
		StringBuilder sb = new StringBuilder(APIConstants.getTaobaoContainerUrl()).append("/container?appkey=").append(
				APIConstants.getAppKey());
		return sb.toString();
	}

	/**
	 * 直接调用淘宝应用API，不经过Service层的校验和判断
	 * 
	 * @param request
	 * @param needSession
	 *            是否需要sessionKey,是则从session中获取sessionKey
	 * @return
	 * @throws ApiException
	 */
	protected <T extends TaobaoResponse> T exec(TaobaoRequest<T> request, boolean needSession) throws ApiException {
		String sessionKey = null;
		if (needSession) {
			sessionKey = getSessionKey();
		}
		return APICaller.call(request, sessionKey);
	}

	/**
	 * 获取session中的淘宝用户sessionKey
	 * 
	 * @return
	 * @throws ApiException
	 */
	protected String getSessionKey() throws ApiException {
		String sessionKey = null;
		UserSession userSession = getUserSession();
		if (userSession != null) {
			sessionKey = userSession.getTop_session();
		} else {
			throw new ApiException("can not find 'userSession' in session,please login first!");
		}
		return sessionKey;
	}

	/**
	 * 获取session中的淘宝用户sessionKey
	 * 
	 * @return
	 * @throws ApiException
	 */
	protected UserSession getUserSession() {
		return (UserSession) WebUtils.getSessionAttribute(getRequest(), UserSession.SESSION_NAME);
	}

	/**
	 * 写json串
	 * 
	 * @param response
	 * @param obj
	 */
	public static void writeJson(ServletResponse response, Object obj) {
		write(response, APICaller.jsonBinder.toJson(obj));
	}

	/**
	 * 写String
	 * 
	 * @param response
	 * @param content
	 */
	public static void write(ServletResponse response, String content) {
		PrintWriter printer = null;
		try {
			response.setContentType("text/html;charset=UTF-8");
			printer = response.getWriter();
			printer.write(content);
		} catch (IOException e) {
			logger.error("response write error", e);
		} finally {
			printer.close();
		}
	}

	/**
	 * 异常捕获
	 * 
	 * @param exception
	 * @return
	 */
	@ExceptionHandler
	protected String otherExceptionHandle(Exception exception, HttpServletRequest request, HttpServletResponse response) {
		logger.error("action caught exception", exception);
		if (checkJsonException(exception, request, response)) {
			return null;
		}
		request.setAttribute(MESSAGE_KEY, "哎呦，系统抽风了，异常信息[" + exception.getMessage() + "]");
		return ERROR_PAGE;
	}

	/**
	 * 处理为json的异常请求
	 * 
	 * @param exception
	 * @param request
	 * @param response
	 * @return
	 */
	private boolean checkJsonException(Exception exception, HttpServletRequest request, HttpServletResponse response) {
		if ("true".equals(request.getParameter("is_json"))) {
			writeJson(response, new ResultObject(0, exception.getMessage()));
			return true;
		}
		return false;
	}

	/**
	 * 组装成功ResultObject对象
	 */
	protected ResultObject successResultObject(String message) {
		return successResultObject(message, null);
	}

	/**
	 * 组装成功ResultObject对象
	 */
	protected ResultObject successResultObject(String message, Object obj) {
		if (obj == null) {
			return new ResultObject(1, message);
		} else {
			return new ResultObject(1, message, obj);
		}
	}
}

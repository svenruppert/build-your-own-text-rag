package com.svenruppert.flow;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import jakarta.servlet.ServletException;

/**
 * Vaadin servlet bootstrap for the application.
 * Registers a session-scoped error handler that logs unhandled exceptions
 * through SLF4J instead of printing them to stderr.
 */
public class AppServlet
    extends VaadinServlet
    implements HasLogger {

  @Override
  protected void servletInitialized()
      throws ServletException {
    super.servletInitialized();
    logger().info("servletInitialized .. started");
    VaadinServletService service = getService();
    service.addSessionInitListener(e ->
        e.getSession().setErrorHandler(
            err -> logger().error("Unhandled session error", err.getThrowable())));
  }


}

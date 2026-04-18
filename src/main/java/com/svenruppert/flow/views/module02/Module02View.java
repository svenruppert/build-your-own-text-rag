package com.svenruppert.flow.views.module02;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route(value = Module02View.PATH, layout = MainLayout.class)
public class Module02View
    extends VerticalLayout
    implements HasLogger {
  public static final String PATH = "Module02";
}

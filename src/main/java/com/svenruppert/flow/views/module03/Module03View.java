package com.svenruppert.flow.views.module03;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route(value = Module03View.PATH, layout = MainLayout.class)
public class Module03View
    extends VerticalLayout
    implements HasLogger {
  public static final String PATH = "Module03";
}

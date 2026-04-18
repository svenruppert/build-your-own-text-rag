package com.svenruppert.flow.views.module05;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route(value = Module05View.PATH, layout = MainLayout.class)
public class Module05View
    extends VerticalLayout
    implements HasLogger {
  public static final String PATH = "Module05";
}

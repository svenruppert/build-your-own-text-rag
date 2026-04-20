package com.svenruppert.flow;

import com.svenruppert.flow.views.AboutView;
import com.svenruppert.flow.views.YoutubeView;
import com.svenruppert.flow.views.glossary.GlossaryView;
import com.svenruppert.flow.views.main.MainView;
import com.svenruppert.flow.views.module01.Module01View;
import com.svenruppert.flow.views.module02.Module02View;
import com.svenruppert.flow.views.module03.Module03View;
import com.svenruppert.flow.views.module04.Module04View;
import com.svenruppert.flow.views.module05.Module05View;
import com.svenruppert.flow.views.module06.Module06View;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;

import static com.vaadin.flow.component.icon.VaadinIcon.*;

public class MainLayout
    extends AppLayout {

  public MainLayout() {
    createHeader();
  }

  private void createHeader() {
    H1 appTitle = new H1("Vaadin Flow Demo");

    SideNav views = getPrimaryNavigation();
    Scroller scroller = new Scroller(views);
    scroller.setClassName(LumoUtility.Padding.SMALL);

    DrawerToggle toggle = new DrawerToggle();
    H2 viewTitle = new H2("Headline");


    HorizontalLayout wrapper = new HorizontalLayout(toggle, viewTitle);
    wrapper.setAlignItems(FlexComponent.Alignment.CENTER);
    wrapper.setSpacing(false);

//    VerticalLayout viewHeader = new VerticalLayout(wrapper, getSecondaryNavigation());
    VerticalLayout viewHeader = new VerticalLayout(wrapper);
    viewHeader.setPadding(false);
    viewHeader.setSpacing(false);

    addToDrawer(appTitle, scroller);
    addToNavbar(viewHeader);

    setPrimarySection(Section.DRAWER);
  }

  private SideNav getPrimaryNavigation() {
    SideNav sideNav = new SideNav();
    sideNav.addItem(new SideNavItem("Dashboard",
                                    "/" + MainView.PATH,
                                    DASHBOARD.create()),
                    new SideNavItem("Module 01",
                                    "/" + Module01View.PATH,
                                    ARROW_CIRCLE_RIGHT.create()),
                    new SideNavItem("Module 02",
                                    "/" + Module02View.PATH,
                                    ARROW_CIRCLE_RIGHT.create()),
                    new SideNavItem("Module 03",
                                    "/" + Module03View.PATH,
                                    ARROW_CIRCLE_RIGHT.create()),
                    new SideNavItem("Module 04",
                                    "/" + Module04View.PATH,
                                    ARROW_CIRCLE_RIGHT.create()),
                    new SideNavItem("Module 05",
                                    "/" + Module05View.PATH,
                                    ARROW_CIRCLE_RIGHT.create()),
                    new SideNavItem("Module 06",
                                    "/" + Module06View.PATH,
                                    ARROW_CIRCLE_RIGHT.create()),
                    new SideNavItem("Youtube",
                                    "/" + YoutubeView.PATH,
                                    CART.create()),
                    new SideNavItem("About",
                                    "/" + AboutView.PATH,
                                    USER_HEART.create()),
                    // Reference material lives below the modules so the
                    // workshop's core progression stays visually primary.
                    new SideNavItem("Glossary",
                                    "/" + GlossaryView.PATH,
                                    BOOK.create())
    );
    return sideNav;
  }

//  private HorizontalLayout getSecondaryNavigation() {
//    HorizontalLayout navigation = new HorizontalLayout();
//    navigation.addClassNames(LumoUtility.JustifyContent.CENTER,
//                             LumoUtility.Gap.SMALL, LumoUtility.Height.MEDIUM);
//    //TODO i18n
//    RouterLink all = createLink("All");
//    RouterLink open = createLink("Open");
//    RouterLink completed = createLink("Completed");
//    RouterLink cancelled = createLink("Cancelled");
//
//    navigation.add(all, open, completed, cancelled);
//    return navigation;
//  }
//
//  private RouterLink createLink(String viewName) {
//    RouterLink link = new RouterLink();
//    link.add(viewName);
//    // Demo has no routes
//     //link.setRoute(YoutubeView.class);
//
//    link.addClassNames(LumoUtility.Display.FLEX,
//                       LumoUtility.AlignItems.CENTER,
//                       LumoUtility.Padding.Horizontal.MEDIUM,
//                       LumoUtility.TextColor.SECONDARY,
//                       LumoUtility.FontWeight.MEDIUM);
//    link.getStyle().set("text-decoration", "none");
//
//    return link;
//  }
}
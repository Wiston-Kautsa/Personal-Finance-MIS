module com.wk.pfmis {
    requires java.desktop;
    requires java.net.http;
    requires java.prefs;
    requires java.sql;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires javafx.controls;
    requires javafx.fxml;

    opens com.wk.pfmis.controllers to javafx.fxml;
    opens com.wk.pfmis.models to javafx.base;

    exports com.wk.pfmis;
    exports com.wk.pfmis.config;
    exports com.wk.pfmis.domain;
    exports com.wk.pfmis.fx;
}

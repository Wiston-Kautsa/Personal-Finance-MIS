module com.wk.pfmis {
    requires java.sql;
    requires javafx.controls;
    requires javafx.fxml;

    opens com.wk.pfmis.controllers to javafx.fxml;
    opens com.wk.pfmis.models to javafx.base;

    exports com.wk.pfmis;
}

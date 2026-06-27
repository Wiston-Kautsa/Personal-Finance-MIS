package com.wk.pfmis;

import com.wk.pfmis.db.DatabaseHandler;

public class DbBootstrap {
    public static void main(String[] args) {
        DatabaseHandler.getInstance().initializeDatabase();
        System.out.println("pfmis.db initialized");
    }
}

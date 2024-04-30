module roomChat {
    requires java.sql;
    requires mysql.connector.j;
    opens Java;
    exports Java;
}
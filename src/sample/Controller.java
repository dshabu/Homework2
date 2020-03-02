package sample;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXListView;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;
import java.util.Scanner;

public class Controller implements Initializable {

    @FXML
    JFXButton populateTableBtn;

    @FXML
    JFXButton openDBBtn;

    @FXML
    JFXButton modifyTableBtn;

    @FXML
    JFXComboBox<String> attribCBox;

    @FXML
    JFXComboBox<String> operatorCBox;

    @FXML
    TextField filterField;

    @FXML
    JFXButton runQueryBtn;

    @FXML
    JFXListView<Student> studentListView;

    private static Connection DB_SRV = null;
    private static final String DB_NAME = "StudentsDB";
    private final String TABLE_NAME = "Students";

    public static void closeDatabase() {
        if (DB_SRV != null) {
            try {
                System.out.println("INFO: Closing Database");
                DB_SRV.commit();
                DB_SRV.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void openConnection() {
        // connect to server
        try {
            String host = null; // required
            int port = 3306;
            String user = "admin";
            String pass = null; // required.
            String connStr = String.format("jdbc:mariadb://%s:%d", host, port);
            DB_SRV = DriverManager.getConnection(connStr, user, pass);
        } catch (SQLException e) {
            System.err.println("ERR: Could not connect to database server.");
            e.printStackTrace();
        }
    }

    /*
     * create and use database.
     * */
    public static void openDatabase() {
        if (DB_SRV != null) {
            // create db and switch to it.
            try (Statement stmt = DB_SRV.createStatement()) {
                stmt.execute("CREATE DATABASE IF NOT EXISTS " + DB_NAME);
                DB_SRV.setCatalog(DB_NAME);
            } catch (SQLException e) {
                System.out.println(e.getErrorCode());
                e.printStackTrace();
            }
        } else {
            System.err.println("ERR: Server not connected.");
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        openConnection();

        openDBBtn.setOnAction(actionEvent -> {
            openDatabase();
            modifyTableBtn.setDisable(false);

            dropTable();
        });

        modifyTableBtn.setOnAction(actionEvent -> {
            createTable();
            populateTable();
            queryTable("SELECT * FROM Students");
            attribCBox.setDisable(false);
            populateTableBtn.setDisable(false);
        });

        populateTableBtn.setOnAction(actionEvent -> queryTable("SELECT * FROM Students"));

        // populate filter options
        ObservableList<String> attribList = attribCBox.getItems();
        attribList.addAll("Name", "Major", "GPA", "Age");

        // watch for attrib changes
        attribCBox.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends String> observableValue, String oldStr, String newStr) -> {
            // fill operatorCBox with common sense operators
            int attribIndex = attribCBox.getSelectionModel().getSelectedIndex();
            fillOperatorList(attribIndex <= 1);

            // disable field until operatorCBox is selected
            filterField.clear();
            filterField.setDisable(true);

            // enable operator, was disabled before.
            operatorCBox.setDisable(false);
        });

        // watch for operator changes
        operatorCBox.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends String> observableValue, String oldStr, String newStr) -> {
            // disable field until operatorCBox is selected
            filterField.setDisable(false);
        });

        // ensure filterField has data, thus enable runQueryBtn
        filterField.textProperty().addListener((observable, oldValue, newValue) -> runQueryBtn.setDisable(newValue.isBlank()));

        runQueryBtn.setOnAction(actionEvent -> {
            String sqlStatement = createQuery();
            if (sqlStatement != null) {
                int numResults = queryTable(sqlStatement);
                System.out.printf("INFO: %d records returned.\n", numResults);
            } else {
                System.err.println("ERR: SQL Statement was Null.");
            }
        });
    }

    /*
     * create table, if it doesn't exist.
     * */
    private void createTable() {
        try (Statement stmt = DB_SRV.createStatement()) {
            String createTable = String.format("CREATE TABLE IF NOT EXISTS %s (" +
                    "id VARCHAR(36) PRIMARY KEY NOT NULL," +
                    "name VARCHAR(50) NOT NULL," +
                    "major VARCHAR(20) NOT NULL," +
                    "gpa DOUBLE NOT NULL," +
                    "age INT NOT NULL)", TABLE_NAME);
            stmt.execute(createTable);
        } catch (SQLException e) {
            System.err.println(e.getErrorCode());
            e.printStackTrace();
        }
    }

    /*
     * drop table, if it exists.
     * */
    private void dropTable() {
        try (Statement stmt = DB_SRV.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /*
     * populate TBNAME table.
     * */
    private void populateTable() {
        // read source data into students array
        Student[] students = readFromFile(20);

        for (Student student : students) {
            try (Statement stmt = DB_SRV.createStatement()) {
                stmt.executeUpdate(student.createInsertStatement(TABLE_NAME));
            } catch (SQLException e) {
                if (e.getErrorCode() == 1062) {
                    System.out.printf("Entity with ID: '%s' already exists.\n", student.getUuid());
                } else {
                    System.err.println(e.getErrorCode());
                    e.printStackTrace();
                }
            }
        }
    }

    /*
     * query the database and return the number
     * of returned results.
     * */
    private int queryTable(String sqlStatement) {
        ObservableList<Student> studentsList = studentListView.getItems();
        studentsList.clear();

        try (Statement stmt = DB_SRV.createStatement()) {
            ResultSet resultSet = stmt.executeQuery(sqlStatement);

            int numResults = 0;
            while (resultSet.next()) {
                numResults++;
                String id = resultSet.getString("id");
                String name = resultSet.getString("name");
                String major = resultSet.getString("major");
                double gpa = resultSet.getDouble("gpa");
                int age = resultSet.getInt("age");

                studentsList.add(new Student(name, major, gpa, id, age));
            }
            return numResults;
        } catch (SQLException e) {
            System.err.println(e.getErrorCode());
            e.printStackTrace();
        }

        return -1;
    }

    /*
     * limits operators when for certain criteria.
     * if user wants to search a string attribute (name, major), only provide contains or does not contain.
     * else, use equals, <=, >=, or !=
     * */
    public void fillOperatorList(boolean stringAttribute) {
        ObservableList<String> operatorList = operatorCBox.getItems();
        operatorList.clear();

        if (stringAttribute) {
            operatorList.addAll("contains", "does not contain");
        } else {
            operatorList.addAll("equals", "less than", "greater than", "does not equal");
        }
    }

    /*
     * using the other controls, construct a SQL
     * statement the initialize method can execute.
     * */
    private String createQuery() {
        String attribute = attribCBox.getSelectionModel().getSelectedItem().toLowerCase();
        String query = filterField.getText();

        if (attribute.equals("name") || attribute.equals("major")) {
            attribute = String.format("upper(%s)", attribute);

            // for string-based queries, uppercase both
            // column and query for 'case-insensitive search'
            query = String.format("upper('%%%s%%')", query);
        }

        String operator;
        switch (operatorCBox.getSelectionModel().getSelectedItem()) {
            case "contains":
                operator = "LIKE";
                break;
            case "does not contain":
                operator = "NOT LIKE";
                break;
            case "equals":
                operator = "=";
                break;
            case "less than":
                operator = "<";
                break;
            case "greater than":
                operator = ">";
                break;
            case "does not equal":
                operator = "<>";
                break;
            default:
                System.err.println("Invalid Operator");
                return null;
        }

        return String.format("SELECT id,name,major,gpa,age FROM %s WHERE %s %s %s", TABLE_NAME, attribute, operator, query);
    }

    /*
     * read student information from file
     * construct an array of type students
     * with specified length.
     * */
    private Student[] readFromFile(int length) {
        Student[] students = new Student[length];

        // ????; some kind of class resource problem.
        File studentsTxt = null;
        try {
            studentsTxt = new File(getClass().getResource("students.txt").toURI());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        if (studentsTxt != null) {
            try (Scanner reader = new Scanner(studentsTxt)) {
                int i = 0;
                while (reader.hasNextLine() && i < length) {
                    String rawLine = reader.nextLine();
                    Scanner lineReader = new Scanner(rawLine).useDelimiter(",");

                    String id = lineReader.next();
                    String name = lineReader.next();
                    String major = lineReader.next();
                    double gpa = lineReader.nextDouble();
                    int age = lineReader.nextInt();

                    students[i] = new Student(name, major, gpa, id, age);
                    i++;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("ERR: Cannot read from students.txt test data.");
        }

        return students;
    }
}

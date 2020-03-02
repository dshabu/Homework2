package sample;

public class Student {
    private double gpa;
    private String name, major, uuid;
    private int age;

    public Student(String name, String major, double gpa, String uuid, int age) {
        this.gpa = gpa;
        this.name = name;
        this.major = major;
        this.uuid = uuid;
        this.age = age;
    }

    public double getGpa() {
        return gpa;
    }

    public String getName() {
        return name;
    }

    public String getMajor() {
        return major;
    }

    public String getUuid() {
        return uuid;
    }

    public int getAge() {
        return age;
    }

    public String createInsertStatement(String tableName) {
        return String.format("INSERT INTO %s VALUES ('%s', '%s', '%s', %.2f, %d)",
                tableName, getUuid(), getName(), getMajor(), getGpa(), getAge());
    }

    @Override
    public String toString() {
        return String.format("%s (%d). Major: %s, GPA: %.2f", getName(), getAge(), getMajor(), getGpa());
    }
}

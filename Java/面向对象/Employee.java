/**
 * Employee 类 — 封装（Encapsulation）示例
 *
 * 体现面向对象三大特性之一：封装
 * - private 字段：外部不可直接访问
 * - public 构造器 + getter：控制访问入口
 * - 方法（mailCheck）：操作内部状态
 */
public class Employee {
    private String name;
    private String address;
    private int number;

    // 构造器：创建对象时初始化
    public Employee(String name, String address, int number) {
        System.out.println("Employee 构造函数被调用");
        this.name = name;
        this.address = address;
        this.number = number;
    }

    // 业务方法
    public void mailCheck() {
        System.out.println("邮寄支票给: " + this.name + " " + this.address);
    }

    // Getter 方法
    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public int getNumber() {
        return number;
    }

    // 测试入口
    public static void main(String[] args) {
        Employee emp = new Employee("张三", "北京市朝阳区", 1001);
        emp.mailCheck();
        System.out.println("姓名: " + emp.getName());
        System.out.println("地址: " + emp.getAddress());
        System.out.println("编号: " + emp.getNumber());
    }
}
/**
 * 多态（Polymorphism）示例
 *
 * 核心概念：同一个接口，使用不同的实例而执行不同的操作
 *
 * 打印机例子：
 *   ├─ print → 彩印  → 彩色纸
 *   └─ print → 黑白印 → 黑白纸
 *
 * Java 中多态的实现依赖「动态绑定」（Dynamic Binding）。
 * C++ 用 virtual 关键字声明虚函数，Java 中默认所有非 static/非 final
 * 方法都是虚函数，子类可以重写。如果不想让方法被重写，用 final 修饰。
 */
public class PolymorphismDemo {

    // ==================== 示例1：Shape 多态 ====================
    static class Shape {
        void draw() {
            System.out.println("Shape 绘制");
        }
    }

    static class Circle extends Shape {
        @Override
        void draw() {
            System.out.println("Circle 绘制圆形");
        }
    }

    static class Rectangle extends Shape {
        @Override
        void draw() {
            System.out.println("Rectangle 绘制矩形");
        }
    }

    // ==================== 示例2：打印机多态 ====================
    interface Printer {
        void print();  // 抽象方法，子类必须实现
    }

    static class ColorPrinter implements Printer {
        @Override
        public void print() {
            System.out.println("彩印 → 输出彩色纸");
        }
    }

    static class BWPrinter implements Printer {
        @Override
        public void print() {
            System.out.println("黑白印 → 输出黑白纸");
        }
    }

    // ==================== main ====================
    public static void main(String[] args) {
        // 多态：父类引用指向子类对象
        System.out.println("--- Shape 多态 ---");
        Shape s1 = new Circle();
        Shape s2 = new Rectangle();
        s1.draw();  // Circle 绘制圆形
        s2.draw();  // Rectangle 绘制矩形

        // 接口多态
        System.out.println("\n--- 打印机多态 ---");
        Printer p1 = new ColorPrinter();
        Printer p2 = new BWPrinter();
        p1.print();  // 彩印 → 输出彩色纸
        p2.print();  // 黑白印 → 输出黑白纸

        // 数组多态
        System.out.println("\n--- 数组多态 ---");
        Shape[] shapes = { new Circle(), new Rectangle(), new Circle() };
        for (Shape s : shapes) {
            s.draw();  // 同一句 s.draw()，不同对象产生不同行为
        }
    }
}
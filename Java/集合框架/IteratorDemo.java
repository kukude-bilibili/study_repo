import java.util.ArrayList;
import java.util.Iterator;

/**
 * ArrayList + Iterator 基础用法
 * 修正要点：
 *   1. import java.util.ArrayIterator → Iterator（ArrayIterator 不存在）
 *   2. While → while（Java 关键字小写）
 *   3. 遍历时删除元素要用 it.remove()，不能直接操作集合
 */
public class IteratorDemo {
    public static void main(String[] args) {
        // 创建集合
        ArrayList<String> sites = new ArrayList<String>();
        sites.add("Google");
        sites.add("Runoob");
        sites.add("Taobao");
        sites.add("Zhihu");

        // 获取迭代器
        Iterator<String> it = sites.iterator();

        // 输出第一个元素
        System.out.println("第一个元素: " + it.next());  // Google

        // 遍历输出所有元素
        System.out.println("\n--- 遍历所有元素 ---");
        while (it.hasNext()) {
            System.out.println(it.next());
        }

        // 遍历过程中安全删除元素（用迭代器自身的 remove）
        System.out.println("\n--- 删除元素后重新遍历 ---");
        Iterator<String> it2 = sites.iterator();
        while (it2.hasNext()) {
            String s = it2.next();
            if (s.equals("Taobao")) {
                it2.remove();  // 安全删除，不会抛 ConcurrentModificationException
            }
        }
        System.out.println("删除后: " + sites);  // [Google, Runoob, Zhihu]
    }
}
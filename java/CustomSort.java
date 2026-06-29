// @sortviz-name MySort
import java.util.*;

public class CustomSort {
    public static void sort(int[] array, SortEngine.StepRecorder rec) {
        //折半插入排序
        int n = array.length;
        // 从第二个元素开始，依次插入到已排序序列中
        for (int i = 1; i < n; i++) {
            int key = array[i];
            int left = 0;
            int right = i - 1;
            // 1. 二分查找确定插入位置
            while (left <= right) {
                int mid = left + (right - left) / 2;
                rec.compare(mid, i);  // 记录比较操作
                if (array[mid] > key) {
                    right = mid - 1;
                } else {
                    left = mid + 1;
                }
            }
            // 此时 left 就是插入位置
            // 2. 将 [left, i-1] 范围内的元素后移一位
            for (int j = i - 1; j >= left; j--) {
                array[j + 1] = array[j];
                rec.swap(j, j + 1);  // 记录移动操作
            }
            // 3. 插入 key
            if (left != i) {
                array[left] = key;
                // 注意：这里没有实际的交换操作，但为了记录步骤，我们可以记录一次虚拟交换
                // 或者使用 rec.mark() 标记当前已排序位置
                rec.mark(left);
            }
        }
    }
}
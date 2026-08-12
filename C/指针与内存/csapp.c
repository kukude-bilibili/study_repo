#include <stdio.h>
int main() {
    int x = 0x01234567;
    unsigned char* p = (unsigned char*)&x;
    for (int i = 0; i < sizeof(int); i++)
        printf("%.2x ", p[i]);
    // 如果输出 67 45 23 01 → 小端
    // 如果输出 01 23 45 67 → 大端
    return 0;
}
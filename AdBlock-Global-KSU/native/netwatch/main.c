#include <stdio.h>
#include <unistd.h>

int main(){
    while(1){
        FILE *fp=fopen("/proc/net/tcp","r");
        if(fp){
            char buf[512];
            while(fgets(buf,sizeof(buf),fp)){
                printf("%s",buf);
            }
            fclose(fp);
        }
        sleep(5);
    }
    return 0;
}

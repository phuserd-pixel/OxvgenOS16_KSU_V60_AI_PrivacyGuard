#include <stdio.h>
#include <string.h>

int main(){
    char domain[256];
    while(fgets(domain,sizeof(domain),stdin)){
        if(strstr(domain,"ad")||strstr(domain,"ads")){
            printf("0.0.0.0\n");
        }
    }
    return 0;
}

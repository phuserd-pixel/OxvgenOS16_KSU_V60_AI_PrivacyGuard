#include <stdio.h>
#include <string.h>

int main(){
    char domain[256];
    while(fgets(domain,sizeof(domain),stdin)){
        if(strstr(domain,"sdk")||strstr(domain,"track")||strstr(domain,"analytics")){
            printf("%s",domain);
        }
    }
    return 0;
}

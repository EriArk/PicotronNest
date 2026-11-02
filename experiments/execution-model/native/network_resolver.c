/* Android resolves names; libc still constructs/frees the normal addrinfo ABI. */
#define _GNU_SOURCE
#include <arpa/inet.h>
#include <dlfcn.h>
#include <errno.h>
#include <netdb.h>
#include <pthread.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/un.h>
#include <unistd.h>

static pthread_once_t once=PTHREAD_ONCE_INIT;
static int (*system_gai)(const char *,const char *,const struct addrinfo *,struct addrinfo **);
static void init(void) { *(void **)(&system_gai)=dlsym(RTLD_NEXT,"getaddrinfo"); }

static int transfer(int fd,void *buffer,size_t size,int writing) {
    unsigned char *bytes=buffer;
    while (size) {
        ssize_t count=writing ? send(fd,bytes,size,MSG_NOSIGNAL) : recv(fd,bytes,size,0);
        if (count<0 && errno==EINTR) continue;
        if (count<=0) return -1;
        bytes+=count; size-=(size_t)count;
    }
    return 0;
}

int getaddrinfo(const char *host,const char *service,const struct addrinfo *hints,struct addrinfo **result) {
    pthread_once(&once,init);
    if (!system_gai || !result) return EAI_SYSTEM;
    *result=NULL;
    const char *channel=getenv("PICONEST_DNS_SOCKET");
    unsigned char numeric[16];
    if (!channel || !host || (hints && (hints->ai_flags&AI_NUMERICHOST)) ||
        inet_pton(AF_INET,host,numeric)==1 || inet_pton(AF_INET6,host,numeric)==1 || strchr(host,'%'))
        return system_gai(host,service,hints,result);
    if (hints && hints->ai_family!=AF_UNSPEC && hints->ai_family!=AF_INET && hints->ai_family!=AF_INET6) return EAI_FAMILY;
    size_t length=strlen(host),channel_length=strlen(channel);
    struct sockaddr_un address={.sun_family=AF_UNIX};
    if (!length || length>253 || !channel_length || channel_length>sizeof(address.sun_path)-2) return EAI_NONAME;
    memcpy(address.sun_path+1,channel,channel_length);
    int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);
    if (fd<0) return EAI_AGAIN;
    struct timeval timeout={.tv_sec=8};
    setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));
    setsockopt(fd,SOL_SOCKET,SO_SNDTIMEO,&timeout,sizeof(timeout));
    int error=EAI_AGAIN;
    if (connect(fd,(struct sockaddr *)&address,offsetof(struct sockaddr_un,sun_path)+1+channel_length)) goto done;
    struct ucred credentials; socklen_t credential_size=sizeof(credentials);
    if (getsockopt(fd,SOL_SOCKET,SO_PEERCRED,&credentials,&credential_size) || credentials.uid!=getuid()) goto done;
    unsigned char request[6]={'P','N','R','1',(unsigned char)(length>>8),(unsigned char)length};
    unsigned char reply[8];
    if (transfer(fd,request,sizeof(request),1) || transfer(fd,(void *)host,length,1) || transfer(fd,reply,sizeof(reply),0)) goto done;
    if (memcmp(reply,"PNR1",4) || reply[5]>16 || reply[6] || reply[7]) goto done;
    if (reply[4]) { error=reply[4]==1 ? EAI_NONAME : EAI_AGAIN; goto done; }
    struct addrinfo **tail=result;
    for (unsigned i=0;i<reply[5];i++) {
        unsigned char size,raw[16]; char text[INET6_ADDRSTRLEN];
        if (transfer(fd,&size,1,0) || (size!=4 && size!=16) || transfer(fd,raw,size,0)) goto done;
        int family=size==4 ? AF_INET : AF_INET6;
        if (hints && hints->ai_family!=AF_UNSPEC && hints->ai_family!=family &&
            !(hints->ai_family==AF_INET6 && (hints->ai_flags&AI_V4MAPPED))) continue;
        if (!inet_ntop(family,raw,text,sizeof(text))) goto done;
        struct addrinfo numeric_hints={0};
        if (hints) numeric_hints=*hints;
        numeric_hints.ai_flags|=AI_NUMERICHOST;
        struct addrinfo *entry=NULL;
        int status=system_gai(text,service,&numeric_hints,&entry);
        if (status==EAI_NONAME || status==EAI_ADDRFAMILY) continue;
        if (status) { error=status; goto done; }
        if (entry && (numeric_hints.ai_flags&AI_CANONNAME)) {
            free(entry->ai_canonname); entry->ai_canonname=strdup(host);
            if (!entry->ai_canonname) { freeaddrinfo(entry); error=EAI_MEMORY; goto done; }
        }
        *tail=entry;
        while (*tail) tail=&(*tail)->ai_next;
    }
    error=*result ? 0 : EAI_NONAME;
done:
    close(fd);
    if (error && *result) { freeaddrinfo(*result); *result=NULL; }
    return error;
}

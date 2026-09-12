#ifndef NATIVE_API_H
#define NATIVE_API_H

#include <stdint.h>

typedef int (*HookFunType)(
        void *func,
        void *replace,
        void **backup
);


typedef int (*UnhookFunType)(
        void *func
);


typedef struct {
    uint32_t version;
    HookFunType hook_func;
    UnhookFunType unhook_func;
} NativeAPIEntries;


typedef void (*NativeOnModuleLoaded)(
        const char *name,
        void *handle
);

#endif
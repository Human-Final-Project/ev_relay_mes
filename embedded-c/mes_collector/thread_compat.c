#include "thread_compat.h"

#include <stdlib.h>

typedef struct {
    CollectorThreadRoutine routine;
    void *context;
} CollectorThreadStart;

#ifdef _WIN32
static DWORD WINAPI collector_thread_adapter(LPVOID parameter)
{
    CollectorThreadStart *start = (CollectorThreadStart *)parameter;
    CollectorThreadRoutine routine = start->routine;
    void *context = start->context;

    free(start);
    routine(context);
    return 0;
}
#else
static void *collector_thread_adapter(void *parameter)
{
    CollectorThreadStart *start = (CollectorThreadStart *)parameter;
    CollectorThreadRoutine routine = start->routine;
    void *context = start->context;

    free(start);
    routine(context);
    return NULL;
}
#endif

int collector_mutex_init(CollectorMutex *mutex)
{
    if (mutex == NULL) {
        return -1;
    }
#ifdef _WIN32
    InitializeCriticalSection(mutex);
    return 0;
#else
    return pthread_mutex_init(mutex, NULL) == 0 ? 0 : -1;
#endif
}

void collector_mutex_destroy(CollectorMutex *mutex)
{
    if (mutex == NULL) {
        return;
    }
#ifdef _WIN32
    DeleteCriticalSection(mutex);
#else
    pthread_mutex_destroy(mutex);
#endif
}

void collector_mutex_lock(CollectorMutex *mutex)
{
#ifdef _WIN32
    EnterCriticalSection(mutex);
#else
    pthread_mutex_lock(mutex);
#endif
}

void collector_mutex_unlock(CollectorMutex *mutex)
{
#ifdef _WIN32
    LeaveCriticalSection(mutex);
#else
    pthread_mutex_unlock(mutex);
#endif
}

int collector_thread_start_detached(CollectorThreadRoutine routine,
                                    void *context)
{
    CollectorThreadStart *start;

    if (routine == NULL) {
        return -1;
    }
    start = (CollectorThreadStart *)malloc(sizeof(*start));
    if (start == NULL) {
        return -1;
    }
    start->routine = routine;
    start->context = context;

#ifdef _WIN32
    {
        HANDLE thread = CreateThread(NULL,
                                     0,
                                     collector_thread_adapter,
                                     start,
                                     0,
                                     NULL);

        if (thread == NULL) {
            free(start);
            return -1;
        }
        CloseHandle(thread);
    }
#else
    {
        pthread_attr_t attributes;
        pthread_t thread;
        int result;

        if (pthread_attr_init(&attributes) != 0) {
            free(start);
            return -1;
        }
        if (pthread_attr_setdetachstate(&attributes,
                                        PTHREAD_CREATE_DETACHED) != 0) {
            pthread_attr_destroy(&attributes);
            free(start);
            return -1;
        }
        result = pthread_create(&thread,
                                &attributes,
                                collector_thread_adapter,
                                start);
        pthread_attr_destroy(&attributes);
        if (result != 0) {
            free(start);
            return -1;
        }
    }
#endif
    return 0;
}

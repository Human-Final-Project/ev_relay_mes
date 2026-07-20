#ifndef _WIN32
#define _POSIX_C_SOURCE 200809L
#endif

#include "thread_compat.h"

#include <stdlib.h>

#ifndef _WIN32
#include <errno.h>
#include <time.h>
#endif

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

int collector_thread_start(CollectorThread *thread,
                           CollectorThreadRoutine routine,
                           void *context)
{
    CollectorThreadStart *start;

    if (thread == NULL || routine == NULL || thread->started) {
        return -1;
    }
    start = (CollectorThreadStart *)malloc(sizeof(*start));
    if (start == NULL) {
        return -1;
    }
    start->routine = routine;
    start->context = context;
#ifdef _WIN32
    thread->handle = CreateThread(NULL,
                                  0,
                                  collector_thread_adapter,
                                  start,
                                  0,
                                  NULL);
    if (thread->handle == NULL) {
        free(start);
        return -1;
    }
#else
    if (pthread_create(&thread->handle,
                       NULL,
                       collector_thread_adapter,
                       start) != 0) {
        free(start);
        return -1;
    }
#endif
    thread->started = 1;
    return 0;
}

int collector_thread_join(CollectorThread *thread)
{
    if (thread == NULL || !thread->started) {
        return -1;
    }
#ifdef _WIN32
    if (WaitForSingleObject(thread->handle, INFINITE) != WAIT_OBJECT_0) {
        return -1;
    }
    CloseHandle(thread->handle);
    thread->handle = NULL;
#else
    if (pthread_join(thread->handle, NULL) != 0) {
        return -1;
    }
#endif
    thread->started = 0;
    return 0;
}

void collector_thread_sleep_milliseconds(unsigned int milliseconds)
{
#ifdef _WIN32
    Sleep((DWORD)milliseconds);
#else
    struct timespec request;

    request.tv_sec = (time_t)(milliseconds / 1000U);
    request.tv_nsec = (long)(milliseconds % 1000U) * 1000000L;
    while (nanosleep(&request, &request) != 0 && errno == EINTR) {
    }
#endif
}

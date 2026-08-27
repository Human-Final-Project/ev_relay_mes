#ifndef MES_COLLECTOR_THREAD_COMPAT_H
#define MES_COLLECTOR_THREAD_COMPAT_H

#ifdef _WIN32
#include <windows.h>
typedef CRITICAL_SECTION CollectorMutex;
#else
#include <pthread.h>
typedef pthread_mutex_t CollectorMutex;
#endif

typedef void (*CollectorThreadRoutine)(void *context);

int collector_mutex_init(CollectorMutex *mutex);
void collector_mutex_destroy(CollectorMutex *mutex);
void collector_mutex_lock(CollectorMutex *mutex);
void collector_mutex_unlock(CollectorMutex *mutex);

/* Starts a detached worker. Linux uses pthread; Windows uses Win32 threads. */
int collector_thread_start_detached(CollectorThreadRoutine routine,
                                    void *context);

#endif

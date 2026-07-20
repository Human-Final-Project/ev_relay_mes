#ifndef MES_COLLECTOR_SCHEDULER_H
#define MES_COLLECTOR_SCHEDULER_H

/* Starts and stops the Backend command polling worker. */
int scheduler_init(void);
void scheduler_cleanup(void);

#endif

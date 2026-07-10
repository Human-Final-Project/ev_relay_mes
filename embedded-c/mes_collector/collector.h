#ifndef MES_COLLECTOR_COLLECTOR_H
#define MES_COLLECTOR_COLLECTOR_H

/*
 * Runs the L2 collector.
 *
 * The base project only verifies module wiring. The TCP accept loop and the
 * connection-per-pthread implementation are added in later implementation
 * stages.
 */
int collector_run(void);

#endif

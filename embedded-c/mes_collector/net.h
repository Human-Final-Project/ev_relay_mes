#ifndef MES_COLLECTOR_NET_H
#define MES_COLLECTOR_NET_H

/* Prepares and releases platform networking resources. */
int net_runtime_init(void);
void net_runtime_cleanup(void);

#endif

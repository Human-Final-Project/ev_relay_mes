#ifndef MES_COLLECTOR_API_CLIENT_H
#define MES_COLLECTOR_API_CLIENT_H

/* Prepares and releases the L2 -> Backend HTTP client. */
int api_client_init(void);
void api_client_cleanup(void);

#endif

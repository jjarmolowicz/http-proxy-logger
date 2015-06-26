http-proxy-logger

This is command line, standalone http proxy that does two things:
- redirects request from local port to given host/port
- writes every exchange to dirs/files (request, headers, response, headers)

Given that, You can "see" the communication between your (Web)services by proxieng communication throu that "man in the middle".
Since everything is written to files You can use any search/grep tool on content. Also data viewer is Your choice.

One instance can bind to one port only, but You can start as many instances (on different ports) as You want.

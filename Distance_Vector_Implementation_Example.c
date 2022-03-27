#define MAX_ROUTES  128 /*maximum size of routing table*/
#define MAX_TTL     120 /* time in seconds until route expires */

typedef struct {
    NodeAddr Destination;   // address of destination
    NodeAddr NextHop;       // address of next hop
    int Cost;               // distance metric
    u_short TTL;            // time to live
} Route;

int numRoutes = 0;
Route routingTable[MAX_ROUTES];

void mergeRoute(Route* new) {
    int i;
    for (int i = 0; i < numRoutes; i++) {
        if (new->Destination == routingTable[i].Destination) {
            if ((new->Cost + 1) < routingTable[i].Cost) {
                // found a better route
                break;            
            } else if {
                // metric for current next-hop may have changed            
                break;
            } else {
                // route is uninteresting, just ignore it
                return;
            }        
        }    
    }
    if (i == numRoutes) {
        // this is a completely new route, is there room for it?
        if (numRoutes < MAXROUTES) {
            numRoutes++;        
        } else {
            // can't fit this route in table, so give up
            return;        
        }
    }
    routingTable[i] = *new;
    routingTable[i].TTL = MAX_TTL; // reset TTL
    ++routingTable[i].Cost;
}

/**
*   main routine that calls mergeRoute to incorporate all routes contained in
*   a routing update that is received from a neighboring node.
*/
void updateRoutingTable(Route* newRoute, int numNewRoutes) {
    int i;
    for (i = 0; i < numNewRoutes; i++) {
        mergeRoute(&newRoute[i]);    
    }
}

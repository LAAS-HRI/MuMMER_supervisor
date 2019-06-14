// Internal action code for project supervisor

package jia;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import arch.ROSAgArch;

//import java.util.logging.Logger;

import jason.asSemantics.*;
import jason.asSyntax.*;
import msg_srv_impl.RouteImpl;
import msg_srv_impl.SemanticRouteResponseImpl;
import ontologenius_msgs.OntologeniusServiceResponse;
import semantic_route_description_msgs.Route;
import semantic_route_description_msgs.SemanticRouteResponse;
import utils.Code;

public class compute_route extends DefaultInternalAction {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
    	String from = args[0].toString();
		from = from.replaceAll("^\"|\"$", "");
		
		ListTerm to_list;
		Term to = args[1];
		// if there is multiple places (toilet and atm cases)
		if(to.isList()) {
			to_list = (ListTerm) to;
		}
		// if there is only one place, we convert it to a list with one element for convenience
		else {
			to_list = new ListTermImpl();
			to_list.add(to);
		}
		List<SemanticRouteResponse> routes = new ArrayList<SemanticRouteResponse>();
		boolean at_least_one_ok = false;
		// we get all the possible routes for the different places 
		// (we will be able then to choose between the best toilet or atm to go)
		for (Term t: to_list) {
			// call the service to compute route
			ROSAgArch.getM_rosnode().call_get_route_srv(from, 
					((StringTermImpl)t).getString(),
					args[2].toString(), 
					Boolean.parseBoolean(args[3].toString()));

			SemanticRouteResponseImpl resp = new SemanticRouteResponseImpl();
			// we wait the result return from the service
			do {
				resp = ROSAgArch.getM_rosnode().get_get_route_resp();
				if (resp != null) {
					routes.add(resp);
				}
				sleep(100);
			}while(resp == null);
			if(resp.getCode() != Code.ERROR.getCode()) {
				at_least_one_ok = true;
			}
		}        
		if(!at_least_one_ok) {
			return false;
		}else {
			int n_routes = Integer.parseInt(args[4].toString());
			
			if(n_routes == 1) {
				RouteImpl route = select_best_route(routes);
				ListTermImpl route_list = new ListTermImpl();
				route_list.addAll(route.getRoute());
				Structure struct = new Structure("route");
				struct.addTerm(new StringTermImpl(route.getGoal()));
				struct.addTerm(route_list);
				return un.unifies(args[5], struct);
			}else if(n_routes == 2) {
				RouteImpl[] best_routes;
				best_routes = select_2_best_routes(routes);
				ListTermImpl list = new ListTermImpl();
				// route 0
				ListTerm route_list = new ListTermImpl();
				String s_route_list = best_routes[0].getRoute().stream()
						  .map(s -> "\"" + s + "\"")
						  .collect(Collectors.joining(", "));
				route_list = ListTermImpl.parseList("["+s_route_list+"]");
				Structure struct = new Structure("route");
				struct.addTerm(new StringTermImpl(best_routes[0].getGoal()));
				struct.addTerm(route_list);
				list.add(struct);
				// route 1
				route_list = new ListTermImpl();
				s_route_list = best_routes[1].getRoute().stream()
						  .map(s -> "\"" + s + "\"")
						  .collect(Collectors.joining(", "));
				route_list = ListTermImpl.parseList("["+s_route_list+"]");
				struct = new Structure("route");
				struct.addTerm(new StringTermImpl(best_routes[1].getGoal()));
				struct.addTerm(route_list);
				list.add(struct);
				return un.unifies(args[5], list);
			}else {
				return false;
			}
			
		}
				
    }
    
    void sleep(long msec) {
		try {
			Thread.sleep(msec);
		} catch (InterruptedException ex) {
		}
	}
    
    public RouteImpl select_best_route(List<SemanticRouteResponse> routes_resp_list) {
		RouteImpl best_route = new RouteImpl();
		float min_cost = Float.MAX_VALUE;
		if (routes_resp_list.size() > 0) {
			for (SemanticRouteResponse route_resp : routes_resp_list) {
				List<Route> routes = route_resp.getRoutes();
				float[] costs = route_resp.getCosts();
				List<String> goals = route_resp.getGoals();

				for (int i = 0; i < routes.size(); i++) {
					if (costs[i] < min_cost) {
						best_route.setRoute(routes.get(i).getRoute());
						best_route.setGoal(goals.get(i));
						min_cost = costs[i];
					}
				}
			}
		}

		return best_route;

	}
	
	public RouteImpl[] select_2_best_routes(List<SemanticRouteResponse> routes_resp_list) {
		RouteImpl[] best_routes = new RouteImpl[2];
		best_routes[0] = new RouteImpl();
		best_routes[1] = new RouteImpl();
		float min_cost1 = Float.MAX_VALUE;
		float min_cost2 = Float.MAX_VALUE;
		int list_size = routes_resp_list.size();
		if (list_size > 2) {
			for (SemanticRouteResponse route_resp : routes_resp_list) {
				List<Route> routes = route_resp.getRoutes();
				float[] costs = route_resp.getCosts();
				List<String> goals = route_resp.getGoals();

				for (int i = 0; i < routes.size(); i++) {
					if (costs[i] < min_cost1) {
						min_cost2 = min_cost1;
						best_routes[0].setRoute(routes.get(i).getRoute());
						best_routes[0].setGoal(goals.get(i));
						min_cost1 = costs[i];
					}else if (costs[i] < min_cost2) {
						min_cost2 = costs[i];
						best_routes[1].setRoute(routes.get(i).getRoute());
						best_routes[1].setGoal(goals.get(i));
					}
				}
			}
		}else {
			return null;
		}

		return best_routes;

	}


}
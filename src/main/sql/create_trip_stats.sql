CREATE MATERIALIZED VIEW matsim_output.trip_stats_{RUN_NAME} AS
	WITH stuttgart_trips AS
			(SELECT t.*, h.regiostar7
			FROM matsim_input.agents_homes_with_raumdata h
			INNER JOIN matsim_output.trips t
			ON h.person_id = t.person
			WHERE h.subpop = 'stuttgart_umland'
			AND t.run_name = $${RUN_NAME}$$),

	trav_time_per_person AS
		(SELECT
		regiostar7,
		person,
		SUM(trav_time) as trav_time,
		SUM(traveled_distance) as trav_dist,
		COUNT(trip_id) as trips
		FROM stuttgart_trips
		GROUP BY regiostar7, person)
		
	SELECT
		regiostar7,
		AVG(trips) as avg_trips,
		AVG(trav_dist) as avg_trav_dist,
		AVG(trav_time) as avg_trav_time,
		(AVG(trav_dist)/AVG(trav_time))*3.6 as avg_trav_speed
	FROM trav_time_per_person
	GROUP BY regiostar7

WITH stuttgart_trips AS
	(SELECT 
	 	t.*,
	 	h.regiostar7,
	 	CASE WHEN main_mode = 'bike' THEN 1 ELSE 0 END As contains_bike,
		CASE WHEN main_mode = 'pt' THEN 1 ELSE 0 END As contains_pt,
		CASE WHEN main_mode = 'ride' THEN 1 ELSE 0 END As contains_ride,
		CASE WHEN main_mode = 'car' THEN 1 ELSE 0 END As contains_car,
		CASE WHEN main_mode = 'walk' THEN 1 ELSE 0 END As contains_walk
	FROM matsim_input.agents_homes_with_raumdata h
	INNER JOIN matsim_output.trips t
	ON h.person_id = t.person
	WHERE h.subpop = 'stuttgart_umland'
	AND t.run_name = $${RUN_NAME}$$),

mode_counts AS (
	SELECT
		regiostar7,
		person,	
		SUM(contains_bike) as bike_s,
		SUM(contains_pt) as pt_s,
		SUM(contains_ride) as ride_s,
		SUM(contains_car) as car_s,
		SUM(contains_walk) as walk_s
	FROM stuttgart_trips
	GROUP BY regiostar7, person),
	
groups AS(

	SELECT
		regiostar7,
		'walk' as modes_used,
		COUNT(person) as person
	FROM mode_counts
	WHERE
		bike_s = 0 AND
		pt_s = 0 AND
		(ride_s = 0 OR car_s = 0) AND
		walk_s > 0
	GROUP BY regiostar7

	UNION

	SELECT
		regiostar7,
		'bike' as modes_used,
		COUNT(person) as person
	FROM mode_counts
	WHERE
		(bike_s > 0 AND
		pt_s = 0 AND
		(ride_s = 0 OR car_s = 0) AND
		walk_s = 0)
	GROUP BY regiostar7

	UNION

	SELECT
		regiostar7,
		'pt' as modes_used,
		COUNT(person) as person
	FROM mode_counts
	WHERE
		(bike_s = 0 AND
		pt_s > 0 AND
		(ride_s = 0 OR car_s = 0) AND
		walk_s = 0)
	GROUP BY regiostar7

	UNION

	SELECT
		regiostar7,
		'car' as modes_used,
		COUNT(person) as person
	FROM mode_counts
	WHERE
		(bike_s = 0 AND
		pt_s > 0 AND
		(ride_s > 0 OR car_s > 0) AND
		walk_s = 0)
	GROUP BY regiostar7

	UNION

	SELECT
		regiostar7,
		'car_bike' as modes_used,
		COUNT(person) as person
	FROM mode_counts
	WHERE
		(bike_s > 0 AND
		pt_s = 0 AND
		(ride_s > 0 OR car_s > 0) AND
		walk_s = 0)
	GROUP BY regiostar7

	UNION

	SELECT
		regiostar7,
		'car_pt' as modes_used,
		COUNT(person) as person
	FROM mode_counts
	WHERE
		(bike_s = 0 AND
		pt_s > 0 AND
		(ride_s > 0 OR car_s > 0) AND
		walk_s = 0)
	GROUP BY regiostar7

	UNION

	SELECT
		regiostar7,
		'bike_pt' as modes_used,
		COUNT(person) as person
	FROM mode_counts
	WHERE
		(bike_s > 0 AND
		pt_s > 0 AND
		(ride_s = 0 OR car_s = 0) AND
		walk_s = 0)
	GROUP BY regiostar7

	UNION

	SELECT
		regiostar7,
		'car_bike_pt' as modes_used,
		COUNT(person) as person
	FROM mode_counts
	WHERE
		(bike_s > 0 AND
		pt_s > 0 AND
		(ride_s > 0 OR car_s > 0) AND
		walk_s = 0)
	GROUP BY regiostar7
	
	)
	
	
SELECT *, ROUND((person / SUM(person) OVER (partition by regiostar7))* 100, 1) AS mode_fragments
FROM groups


CREATE MATERIALIZED VIEW matsim_output.modal_split AS
	WITH stuttgart_trips AS
		(SELECT t.*, h.regiostar7
		FROM matsim_input.agents_homes_with_raumdata h
		INNER JOIN matsim_output.trips t
		ON h.person_id = t.person
		WHERE h.subpop = 'stuttgart_umland'
		AND t.run_name = $${RUN_NAME}$$),

	trip_counts AS
		(SELECT
			regiostar7,
			main_mode,
			COUNT(trip_number) as no_trips
		FROM stuttgart_trips
		GROUP BY regiostar7, main_mode)

	SELECT
	  *,  ROUND((no_trips / SUM(no_trips) OVER (partition by regiostar7))* 100, 1) AS mode_share
	FROM
		trip_counts
	
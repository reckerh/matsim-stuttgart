SELECT l.*
FROM matsim_output.legs l
LEFT JOIN matsim_output.trips t
ON
	t.run_name = l.run_name AND
	t.trip_id = l.trip_id
WHERE t.m_main_mode = 'pt_with_bike_used'
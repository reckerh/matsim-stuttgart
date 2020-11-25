CREATE MATERIALIZED VIEW matsim_input.agents_homes_with_raumdata AS
    WITH homes AS
	(SELECT
		agent.person_id,
    	agent.geometry,
    	gem.ags,
    	gem.gen,
    	gem.bez,
    	gem.regiostar7
		FROM (matsim_input.agent_home_locations agent
		INNER JOIN general.gemeinden gem
		ON (st_within(agent.geometry, gem.geometry))))

	SELECT
		homes.*,
		COALESCE(sa.subpop,'outside_stuttgart_umland') AS subpop
	FROM homes
	LEFT JOIN general.sim_area sa
	ON (st_within(homes.geometry, sa.geometry));
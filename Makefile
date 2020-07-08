
JAR := matsim-duesseldorf-*.jar

export SUMO_HOME := $(abspath ../../sumo-1.6.0/)

.PHONY: prepare

$(JAR):
	mvn package

# Required files
scenarios/input/network.osm.pbf:
	curl https://download.geofabrik.de/europe/germany/nordrhein-westfalen/duesseldorf-regbez-latest.osm.pbf\
	  -o scenarios/input/network.osm.pbf

scenarios/input/gtfs.zip:
	curl https://openvrr.de/dataset/c415abd6-3b63-4a1f-8a17-9b77cf5f09ec/resource/52d90889-8b74-4f1e-b3ee-1dc8af70d164/download/2020_03_03_google_transit_verbundweit_inkl_spnv.zip\
	  -o scenarios/input/gtfs.zip

scenarios/input/network.osm: scenarios/input/network.osm.pbf
	osmconvert64 $< -o=$@ -b=6.62,51.12,7,51.32

scenarios/input/sumo.net.xml: scenarios/input/network.osm

	$(SUMO_HOME)/bin/netconvert --geometry.remove --ramps.guess --junctions.join --tls.discard-simple --tls.join\
	 --type-files $(SUMO_HOME)/data/typemap/osmNetconvert.typ.xml,$(SUMO_HOME)/data/typemap/osmNetconvertUrbanDe.typ.xml\
	 --roundabouts.guess --remove-edges.isolated\
	 --no-internal-links --keep-edges.by-vclass passenger --remove-edges.by-type highway.track,highway.services,highway.unsurfaced\
	 --remove-edges.by-vclass hov,tram,rail,rail_urban,rail_fast,pedestrian\
	 --output.original-names --output.street-names\
	 --proj "+proj=utm +zone=32 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs"\
	 --osm-files $< -o=$@


scenarios/input/duesseldorf-network.xml.gz: scenarios/input/sumo.net.xml
	java -jar $(JAR) prepare network $< --output $@

scenarios/input/duesseldorf-network-with-pt.xml.gz: scenarios/input/duesseldorf-network.xml.gz scenarios/input/gtfs.zip
	java -jar $(JAR) prepare transit --network $<

scenarios/duesseldorf-25pct/input/duesseldorf-25pct.plans.xml.gz:
	java -jar $(JAR) prepare population\
	 --population ../../shared-svn/komodnext/matsim-input-files/duesseldorf-senozon/optimizedPopulation_filtered.xml.gz\
	 --attributes  ../../shared-svn/komodnext/matsim-input-files/duesseldorf-senozon/personAttributes.xml.gz


# Aggregated target
prepare: scenarios/duesseldorf-25pct/input/duesseldorf-25pct.plans.xml.gz scenarios/input/duesseldorf-network-with-pt.xml.gz
	echo "Done"
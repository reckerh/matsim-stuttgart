import gzip
import click
import pandas as pd
import geopandas as gpd
import logging
from utils import load_df_to_database, load_db_parameters
from shapely.geometry.linestring import LineString
import matsim


@click.group()
def cli():
    pass


# ------------------------------------------
# ------------------------------------------
# IMPORT FUNCTIONS
# ------------------------------------------
# ------------------------------------------

# ------------------------------------------
# Update-run-tables
# ------------------------------------------
@cli.command()
@click.option('--mode', type=str, default='', help='run mode')
@click.option('--run_dir', type=str, default='', help='run directory [dir]')
@click.option('--db_parameter', type=str, default='', help='path to db_parameter [.json]')
@click.pass_context
def update_run_tables(ctx, mode, run_dir, db_parameter):
    # -- CMD INSTRUCTIONS --
    # python sim_import update-run-tables
    # --mode [run mode: [trips, events, all]]
    # --run_dir [run output directory]
    # --db_parameter [path of db parameter json]

    # -- PRE-CALCULATIONS --
    run_name = run_dir.rsplit("/", 1)[1].replace("output-", "")
    logging.info("Start import run: " + run_name)

    if mode not in ['trips', 'events', 'all']:
        raise Exception("Unknown mode parameter. Use either one of these: 'trips', 'events', 'all'")
    else:
        if mode in ['trips', 'all']:
            trips = run_dir + "/" + run_name + ".output_trips.csv.gz"
            import_trips(trips, db_parameter, run_name)

        if mode in ['events', 'all']:
            events = run_dir + "/" + run_name + ".output_events.xml.gz"
            import_events(events, db_parameter, run_name)

    logging.info("Import run successful: " + run_name )


# ------------------------------------------
# Trip import
# ------------------------------------------
def import_trips(trips, db_parameter, run_name):
    logging.info("Update trips table...")
    # -- PRE-CALCULATIONS --
    gdf_trips = parse_trips_file(trips)
    gdf_trips['run_name'] = run_name

    # -- IMPORT --
    table_name = 'trips'
    table_schema = 'matsim_output'
    db_parameter = load_db_parameters(db_parameter)

    DATA_METADATA = {
        'title': 'Trips',
        'description': 'Trip table',
        'source_name': 'Senozon Input',
        'source_url': 'Nan',
        'source_year': '2020',
        'source_download_date': 'Nan',
    }

    logging.info("Load data to database...")
    load_df_to_database(
        df=gdf_trips,
        update_mode='append',
        db_parameter=db_parameter,
        schema=table_schema,
        table_name=table_name,
        meta_data=DATA_METADATA,
        geom_cols={'geometry': 'LINESTRING'})

    logging.info("Trip import successful!")


# ------------------------------------------
# Events import
# ------------------------------------------
def import_events(events, db_parameter, run_name):
    logging.info("Update events table...")
    # -- PRE-CALCULATIONS --
    events = matsim.event_reader(events)
    df_events = list()
    for event in events:
        df_events.append(event)

    df_events = pd.DataFrame(df_events)
    df_events['run_name'] = run_name

    # -- IMPORT --
    table_name = 'events'
    table_schema = 'matsim_output'
    db_parameter = load_db_parameters(db_parameter)

    DATA_METADATA = {
        'title': 'Events',
        'description': 'Events table',
        'source_name': 'Senozon Input',
        'source_url': 'Nan',
        'source_year': '2020',
        'source_download_date': 'Nan',
    }

    logging.info("Load data to database...")
    load_df_to_database(
        df=df_events,
        update_mode='append',
        db_parameter=db_parameter,
        schema=table_schema,
        table_name=table_name,
        meta_data=DATA_METADATA,
        geom_cols={'geometry': 'LINESTRING'})

    logging.info("Event import successful!")


# ------------------------------------------
# ------------------------------------------
# FURTHER UTIL FUNCTIONS
# ------------------------------------------
# ------------------------------------------

def parse_trips_file(trips):
    logging.info("Parse trips file...")
    try:
        with gzip.open(trips) as f:
            df_trips = pd.read_csv(f, sep=";")
    except OSError as e:
        raise Exception(e.strerror)

    logging.info("Build trip geometries...")
    df_trips['geometry'] = df_trips.apply(
        lambda x: LineString([(x['start_x'], x['start_y']), (x['end_x'], x['end_y'])]), axis=1)
    gdf_trips = gpd.GeoDataFrame(df_trips.drop(columns=['geometry']), geometry=df_trips['geometry'])
    gdf_trips = gdf_trips.set_crs(epsg=25832)

    logging.info("Identify main modes...")
    gdf_trips['main_mode'] = gdf_trips['modes'].apply(identify_main_mode)

    logging.info("Convert times and add additional...")
    gdf_trips['dep_time'] = gdf_trips['dep_time'].apply(convert_time)
    gdf_trips['trav_time'] = gdf_trips['trav_time'].apply(convert_time)
    gdf_trips['wait_time'] = gdf_trips['wait_time'].apply(convert_time)
    gdf_trips['arr_time'] = gdf_trips['dep_time'] + gdf_trips['trav_time'] + gdf_trips['wait_time']
    gdf_trips['trip_speed'] = gdf_trips.apply(lambda x:
                                              calculate_speed(x['traveled_distance'], x['trav_time'] + x['wait_time']),
                                              axis=1
                                              )
    gdf_trips['beeline_speed'] = gdf_trips.apply(lambda x:
                                                 calculate_speed(x['euclidean_distance'],
                                                                 x['trav_time'] + x['wait_time']),
                                                 axis=1
                                                 )

    logging.info("Trip table manipulation finished...")
    return gdf_trips


def identify_main_mode(mode_string):
    if 'pt' in mode_string:
        return 'pt'
    elif 'car' in mode_string:
        return 'car'
    elif 'ride' in mode_string:
        return 'ride'
    elif 'bike' in mode_string:
        return 'bike'
    else:
        return 'walk'


def convert_time(time_string):
    time = time_string.split(":")
    time = list(map(int, time))
    return time[0]*3600+time[1]*60+time[2]


def calculate_speed(distance, time):
    if time == 0:
        return 0
    else:
        return (distance/time)*3.6


# ------------------------------------------
# ------------------------------------------


if __name__ == '__main__':
    cli(obj={})

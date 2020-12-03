import gzip
import click
import pandas as pd
import geopandas as gpd
import logging
from utils import load_df_to_database, load_db_parameters
import xml.etree.ElementTree as ET
from shapely.geometry.linestring import LineString
import psycopg2 as pg


@click.group()
def cli():
    pass


@cli.command()
@click.option('--run_dir', type=str, default='', help='run directory [dir]')
@click.option('--db_parameter', type=str, default='', help='path to db_parameter [.json]')
@click.pass_context
def import_run_data(ctx, run_dir, db_parameter):
    """
    This is the function which can be executed for importing all relevant data
    of one run output to a postgres database.
    >> Import trips output
    >> Create or update materialized views for calibration

    ---------
    Execution:
    python sim_import update-run-tables
    --run_dir [run output directory]
    --db_parameter [path of db parameter json]
    ---------

    """

    # -- PRE-CALCULATIONS --
    run_name = run_dir.rsplit("/", 1)[1].replace("output-", "")
    logging.info("Start importing run: " + run_name)

    trips = run_dir + "/" + run_name + ".output_trips.csv.gz"
    import_trips(trips, db_parameter, run_name)
    update_views(db_parameter)

    config_param = run_dir + "/" + run_name + ".output_config.xml"
    import_config_param(config_param, db_parameter, run_name)

    logging.info("All data successfully imported: " + run_name)


def import_trips(trips, db_parameter, run_name):
    """
    Trip import based off .output_trips.csv.gz
    """

    logging.info("Create or update trips table with data of: " + run_name)

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

    logging.info("Trip table update successful!")


def parse_trips_file(trips):
    """
    Function for parsing trip file and manipulating trip data
    """

    # -- PARSING --
    logging.info("Parse trips file...")
    try:
        with gzip.open(trips) as f:
            df_trips = pd.read_csv(f, sep=";")
    except OSError as e:
        raise Exception(e.strerror)

    # -- BUILDING GEOMETRIES --
    logging.info("Build trip geometries...")
    df_trips['geometry'] = df_trips.apply(
        lambda x: LineString([(x['start_x'], x['start_y']), (x['end_x'], x['end_y'])]), axis=1)
    gdf_trips = gpd.GeoDataFrame(df_trips.drop(columns=['geometry']), geometry=df_trips['geometry'])
    gdf_trips = gdf_trips.set_crs(epsg=25832)

    # -- FURTHER DATA MANIPULATIONS --
    logging.info("Further data manipulations...")
    gdf_trips['main_mode'] = gdf_trips['modes'].apply(identify_main_mode)
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


def update_views(db_parameter):
    """
    Function for executing sql scripts that create/ update trip output materialized views
    """

    db_parameter = load_db_parameters(db_parameter)
    views = ['matsim_output.distanzklassen',
             'matsim_output.modal_split',
             'matsim_output.nutzersegmente']

    for view in views:
        query = f'''
        REFRESH MATERIALIZED VIEW {view};
        '''
        with pg.connect(**db_parameter) as con:
            cursor = con.cursor()
            cursor.execute(query)
            con.commit()


def import_config_param(config_param, db_parameter, run_name):
    """
    Function importing config parameter namely score parameters for modes
    """

    logging.info("Update mode param table...")

    # -- PRE-CALCULATIONS --
    tree = ET.parse(config_param)
    root = tree.getroot()

    df_modeParameters = list()
    for modeParameters in root.findall("./module[@name='planCalcScore']/parameterset[@type='scoringParameters']/parameterset[@type='modeParams']"):
        row = dict()
        for node in modeParameters:
            row[node.attrib.get("name")] = node.attrib.get("value")
        df_modeParameters.append(row)
    df_modeParameters = pd.DataFrame(df_modeParameters)
    df_modeParameters['run_name'] = run_name

    # -- IMPORT --
    table_name = 'mode_param'
    table_schema = 'matsim_input'
    db_parameter = load_db_parameters(db_parameter)

    DATA_METADATA = {
        'title': 'Mode Parameter',
        'description': 'Mode parameter table',
        'source_name': 'Own Input',
        'source_url': 'Nan',
        'source_year': '2020',
        'source_download_date': 'Nan',
    }

    logging.info("Load data to database...")
    load_df_to_database(
        df=df_modeParameters,
        update_mode='append',
        db_parameter=db_parameter,
        schema=table_schema,
        table_name=table_name,
        meta_data=DATA_METADATA)

    logging.info("Mode param import successful!")


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


if __name__ == '__main__':
    cli(obj={})

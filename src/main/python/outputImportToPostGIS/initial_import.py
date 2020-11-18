import pandas as pd
import geopandas as gpd
import click
import matsim
import logging
from utils import load_df_to_database, load_db_parameters, drop_table_if_exists
from shapely.geometry.multipolygon import MultiPolygon


@click.group()
def cli():
    pass


# ------------------------------------------
# HOME LOCATIONS
@cli.command()
@click.option('--plans', type=str, default='', help='plans path')
@click.option('--db_parameter', type=str, default='', help='Directory of where db parameter are stored')
@click.pass_context
def import_home_loc(ctx, plans, db_parameter):

    # -- CMD INSTRUCTIONS --
    # python initial_import import-home-loc --plans [plan file path] -- db_parameter [path of db parameter json]

    # -- PRE-CALCULATIONS --
    logging.info("Read plans file...")
    plans = matsim.plan_reader(plans, selectedPlansOnly=True)

    home_activity_prefix = 'home'
    agents = list()

    logging.info("Extract relevant information...")
    for person, plan in plans:
        home_activity = next(
            e for e in plan if (e.tag == 'activity' and e.attrib['type'].startswith(home_activity_prefix)))
        agents.append({'person_id': person.attrib['id'], 'home_x': home_activity.attrib['x'],
                       'home_y': home_activity.attrib['y']})

    df_agents = pd.DataFrame(agents)
    gdf_agents = gpd.GeoDataFrame(df_agents.drop(columns=['home_x', 'home_y']),
                                  geometry=gpd.points_from_xy(df_agents.home_x, df_agents.home_y))
    gdf_agents = gdf_agents.set_crs(epsg=25832)

    # -- IMPORT --
    table_name = 'agent_home_locations'
    table_schema = 'matsim_input'
    db_parameter = load_db_parameters(db_parameter)
    drop_table_if_exists(db_parameter, table_name, table_schema)

    DATA_METADATA = {
        'title': 'Agent Home Locations',
        'description': 'Table of agents home locations',
        'source_name': 'Senozon Input',
        'source_url': 'Nan',
        'source_year': '2020',
        'source_download_date': 'Nan',
    }

    logging.info("Load data to database...")
    load_df_to_database(
        df=gdf_agents,
        db_parameter=db_parameter,
        schema=table_schema,
        table_name=table_name,
        meta_data=DATA_METADATA,
        geom_cols={'geometry': 'POINT'})

    logging.info("Home location import successful!")

# ------------------------------------------


# ------------------------------------------
# VG 250
@cli.command()
@click.option('--gem', type=str, default='', help='vg250 path')
@click.option('--db_parameter', type=str, default='', help='Directory of where db parameter are stored')
@click.pass_context
def import_gem(ctx, gem, db_parameter):

    # -- CMD INSTRUCTIONS --
    # python initial_import import-vg-250-gem --vg250 [shape file path] -- db_parameter [path of db parameter json]

    # -- PRE-CALCULATIONS --
    logging.info("Read-in shape file...")
    gdf_gemeinden = gpd.read_file(gem)

    logging.info("Clean-up data...")
    gdf_gemeinden['geometry'] = gdf_gemeinden.apply(lambda x:
                                                  x['geometry'] if x['geometry'].type == 'MultiPolygon' else
                                                  MultiPolygon([x['geometry']]),
                                                  axis=1)
    gdf_gemeinden = gdf_gemeinden.to_crs("epsg:25832")

    # -- IMPORT --
    table_name = 'vg250_gemeinden'
    table_schema = 'general'
    db_parameter = load_db_parameters(db_parameter)
    drop_table_if_exists(db_parameter, table_name, table_schema)

    DATA_METADATA = {
        'title': 'Vg 250 Gemeinden',
        'description': 'Verwaltungsgebiete 1:250000 (Ebenen), Stand 01.01.',
        'source_name': 'Bundesamt für Kartographie und Geodäsie',
        'source_url': 'https://gdz.bkg.bund.de/index.php/default/verwaltungsgebiete-1-250-000-ebenen-stand-01-01-vg250-ebenen-01-01.html',
        'source_year': '2020',
        'source_download_date': '2020-11-17',
    }

    logging.info("Load data to database...")
    load_df_to_database(
        df=gdf_gemeinden,
        db_parameter=db_parameter,
        schema=table_schema,
        table_name=table_name,
        meta_data=DATA_METADATA,
        geom_cols={'geometry': 'MULTIPOLYGON'})

    logging.info("VG 250 Gemeinden import successful!")

# ------------------------------------------


@cli.command()
@click.option('--regiostar', type=str, default='', help='regiostar path')
@click.option('--db_parameter', type=str, default='', help='Directory of where db parameter are stored')
@click.pass_context
def import_regiostar(ctx, regiostar, db_parameter):
    click.echo(regiostar)
    click.echo(db_parameter)


if __name__ == '__main__':
    cli(obj={})

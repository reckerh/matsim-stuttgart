import os
import pandas as pd
import geopandas as gpd
import click
import matsim
import logging
from utils import load_df_to_database, load_db_parameters, drop_table_if_exists, run_sql_script
from shapely.geometry.multipolygon import MultiPolygon


@click.group()
def cli():
    pass


@cli.command()
@click.option('--plans', type=str, default='', help='plans path')
@click.option('--db_parameter', type=str, default='', help='Directory of where db parameter are stored')
@click.pass_context
def import_home_loc(ctx, plans, db_parameter):
    """
    HOME LOCATIONS
    This is the function which can be executed for extracting the agents home locations from the plans file.

    ---------
    Execution:
    python initial_import import-home-loc
    --plans [plan file path]
    --db_parameter [path of db parameter json]
    ---------

    """

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
        update_mode='replace',
        db_parameter=db_parameter,
        schema=table_schema,
        table_name=table_name,
        meta_data=DATA_METADATA,
        geom_cols={'geometry': 'POINT'})

    logging.info("Home location import successful!")


@cli.command()
@click.option('--gem', type=str, default='', help='path to vg250 data [.shp]')
@click.option('--regiosta', type=str, default='', help='path to regioSta data [.xlsx]')
@click.option('--db_parameter', type=str, default='', help='path to db_parameter [.json]')
@click.pass_context
def import_gem(ctx, gem, regiosta, db_parameter):
    """
    COMMUNITY AREA WITH REGIOSTAR ASSIGNMENT
    This is the function which can be executed for uploading community data/ shapes with regiostar assignments to db

    ---------
    Execution:
    python initial_import import-vg-250-gem
    --vg250 [shape file path]
    -- db_parameter [path of db parameter json]
    ---------

    """

    # -- PRE-CALCULATIONS --
    # Step 1: vg250
    logging.info("Read-in community shape file...")
    gdf_gemeinden = gpd.read_file(gem)
    gdf_gemeinden['geometry'] = gdf_gemeinden.apply(lambda x:
                                                    x['geometry'] if x['geometry'].type == 'MultiPolygon' else
                                                    MultiPolygon([x['geometry']]),
                                                    axis=1)
    gdf_gemeinden = gdf_gemeinden.to_crs("epsg:25832")

    # Step 2: regiostar
    logging.info("Read-in regiostart data...")
    df_regiosta = pd.read_excel(regiosta, sheet_name='ReferenzGebietsstand2018')
    df_regiosta['gem'] = df_regiosta.apply(lambda x: str(x['gem']).zfill(8), axis=1)
    df_regiosta = df_regiosta[['gem', 'RegioStaR2', 'RegioStaR4', 'RegioStaR5', 'RegioStaR7']]
    df_regiosta.rename(columns={'gem': 'AGS'}, inplace=True)

    r2 = {1: 'Stadtregion',
          2: 'Ländliche Region'}

    r4 = {11: 'Metropolitane Stadtregion',
          12: 'Regiopolitane Stadtregion',
          21: 'Stadtregionsnahe ländliche Region',
          22: 'Periphere ländliche Region',
          }

    r5 = {51: 'Stadtregion - Metropole',
          52: 'Stadtregion - Regiopole und Großstadt',
          53: 'Stadtregion - Umland',
          54: 'Ländliche Region - Städte, städtischer Raum',
          55: 'Ländliche Region - Kleinstädtischer, dörflicher Raum',
          }

    r7 = {71: 'Stadtregion - Metropole',
          72: 'Stadtregion - Regiopole und Großstadt',
          73: 'Stadtregion - Mittelstadt, städtischer Raum',
          74: 'Stadtregion - Kleinstädtischer, dörflicher Raum',
          75: 'Ländliche Region - Zentrale Stadt ',
          76: 'Ländliche Region - Städtischer Raum',
          77: 'Ländliche Region - Kleinstädtischer, dörflicher Raum',
          }

    df_regiosta['RegioStaR2_bez'] = df_regiosta['RegioStaR2'].replace(r2)
    df_regiosta['RegioStaR4_bez'] = df_regiosta['RegioStaR4'].replace(r4)
    df_regiosta['RegioStaR5_bez'] = df_regiosta['RegioStaR5'].replace(r5)
    df_regiosta['RegioStaR7_bez'] = df_regiosta['RegioStaR7'].replace(r7)

    df_regiosta['RegioStaR2'] = df_regiosta['RegioStaR2'].astype('str')
    df_regiosta['RegioStaR4'] = df_regiosta['RegioStaR4'].astype('str')
    df_regiosta['RegioStaR5'] = df_regiosta['RegioStaR5'].astype('str')
    df_regiosta['RegioStaR7'] = df_regiosta['RegioStaR7'].astype('str')

    logging.info("Merge community and regiostar data...")
    gdf_gemeinden = gdf_gemeinden.merge(df_regiosta, how='left', on='AGS')
    gdf_gemeinden.columns = gdf_gemeinden.columns.map(str.lower)

    # -- IMPORT --
    table_name = 'gemeinden'
    table_schema = 'general'
    db_parameter = load_db_parameters(db_parameter)
    drop_table_if_exists(db_parameter, table_name, table_schema)

    DATA_METADATA = {
        'title': 'Gemeinden (VG250) mit ReioSta Zuordnung',
        'description': 'Verwaltungsgebiete 1:250000 (Ebenen), Stand 01.01.',
        'source_name': 'Bundesamt für Kartographie und Geodäsie',
        'source_url': 'https://gdz.bkg.bund.de/index.php/default/verwaltungsgebiete-1-250-000-ebenen-stand-01-01-vg250-ebenen-01-01.html',
        'source_year': '2020',
        'source_download_date': '2020-11-17',
    }

    logging.info("Load data to database...")
    load_df_to_database(
        df=gdf_gemeinden,
        update_mode='replace',
        db_parameter=db_parameter,
        schema=table_schema,
        table_name=table_name,
        meta_data=DATA_METADATA,
        geom_cols={'geometry': 'MULTIPOLYGON'})

    logging.info("Import of community data successful!")


@cli.command()
@click.option('--calib', type=str, default='', help='path to calib data [.xlsx]')
@click.option('--db_parameter', type=str, default='', help='path to db_parameter [.json]')
@click.pass_context
def import_calib(ctx, calib, db_parameter):
    """
    CALIBRATION DATA
    This is the function which can be executed for uploading community data/ shapes with regiostar assignments to db

    ---------
    Execution:
    python initial_import import-calib
    -- calib [xlsx file path]
    -- db_parameter [path of db parameter json]
    ---------

    """

    # -- PRE-CALCULATIONS --
    logging.info("Read-in excel file...")
    tables = dict()
    tables['calib_distanzklassen'] = pd.read_excel(calib, sheet_name='01_Distanzklassen_Tidy')
    tables['calib_wege'] = pd.read_excel(calib, sheet_name='02_Wege_Tidy')
    tables['calib_modal_split'] = pd.read_excel(calib, sheet_name='03_ModalSplit_Tidy')
    tables['calib_nutzersegmente'] = pd.read_excel(calib, sheet_name='04_Nutzersegmente', skipfooter=7)
    tables['calib_oev_segmente'] = pd.read_excel(calib, sheet_name='05_ÖVSegmente', skipfooter=8)

    for df in tables.values():
        df.columns = df.columns.map(str.lower)

    # -- META DATA --
    DATA_METADATA = {'calib_distanzklassen': {
        'title': 'Distanzklassen',
        'description': 'Tabelle A W12 Wegelänge - Stadt Stuttgart',
        'source_name': 'Tabellarische Grundausertung Stadt Stuttgart. MID 2017',
        'source_url': 'https://vm.baden-wuerttemberg.de/fileadmin/redaktion/m-mvi/intern/Dateien/PDF/MID2017_Stadt_Stuttgart.pdf',
        'source_year': '2018',
        'source_download_date': '2020-11-20'
    }, 'calib_wege': {
        'title': 'Wege',
        'description': 'Allgemeine Kennwerte und Verkehrsaufkommen nach regionalstatistischem Raumtyp (RegioSta R7)',
        'source_name': 'infas. MID 2017',
        'source_url': 'http://gecms.region-stuttgart.org/Download.aspx?id=104816',
        'source_year': '2019',
        'source_download_date': '2020-11-20'
    }, 'calib_modal_split': {
        'title': 'Modal Split',
        'description': 'Allgemeine Kennwerte und Verkehrsaufkommen nach regionalstatistischem Raumtyp (RegioSta R7)',
        'source_name': 'infas. MID 2017',
        'source_url': 'http://gecms.region-stuttgart.org/Download.aspx?id=104816',
        'source_year': '2019',
        'source_download_date': '2020-11-20'
    }, 'calib_nutzersegmente': {
        'title': 'Nutzersegmente',
        'description': 'Allgemeine Kennwerte und Verkehrsaufkommen nach regionalstatistischem Raumtyp (RegioSta R7)',
        'source_name': 'infas. MID 2017',
        'source_url': 'http://gecms.region-stuttgart.org/Download.aspx?id=104816',
        'source_year': '2019',
        'source_download_date': '2020-11-20'
    }, 'calib_oev_segmente': {
        'title': 'Fahrtenanteile je Verkehrsmittel',
        'description': 'Fahrtenanteile je Verkehrsmittel bis zum Ums',
        'source_name': 'VVS',
        'source_url': 'https://www.vvs.de/download/Zahlen-Daten-Fakten-2019.pdf',
        'source_year': '2020',
        'source_download_date': '2020-11-20'
    }
    }

    # -- IMPORT --
    db_parameter = load_db_parameters(db_parameter)

    for key in tables:
        table_schema = 'general'
        drop_table_if_exists(db_parameter, key, table_schema)
        logging.info("Load data to database: " + key)
        load_df_to_database(
            df=tables[key],
            update_mode='replace',
            db_parameter=db_parameter,
            schema=table_schema,
            table_name=key,
            meta_data=DATA_METADATA[key])

    logging.info("Import of calibration data successful!")


@cli.command()
@click.option('--sim_area', type=str, default='', help='sim area shape file')
@click.option('--reg_stuttgart', type=str, default='', help='sim area shape file')
@click.option('--db_parameter', type=str, default='', help='Directory of where db parameter are stored')
@click.pass_context
def import_areas(ctx, sim_area, reg_stuttgart, db_parameter):
    """
    SIMULATION AREA
    This is the function which can be executed for uploading the simulation area and region stuttgart shape file

    ---------
    Execution:
    python initial_import import-sim-area
    --sim_area [shp]
    --reg_stuttgart [shp]
    --db_parameter [path of db parameter json]
    ---------

    """

    # -- PRE-CALCULATIONS --
    logging.info("Read shape files...")
    gdf_sim_area = gpd.read_file(sim_area)
    gdf_sim_area['geometry'] = gdf_sim_area['geometry'].apply(lambda x: MultiPolygon([x]))
    gdf_reg_stuttgart = gpd.read_file(reg_stuttgart)
    gdf = gdf_sim_area.append(gdf_reg_stuttgart)
    gdf = gdf.set_crs(epsg=25832)

    # -- IMPORT --
    table_name = 'areas'
    table_schema = 'general'
    db_parameter = load_db_parameters(db_parameter)
    drop_table_if_exists(db_parameter, table_name, table_schema)

    DATA_METADATA = {
        'title': 'Areas',
        'description': 'Important areas',
        'source_name': 'Nan',
        'source_url': 'Nan',
        'source_year': 'Nan',
        'source_download_date': 'Nan',
    }

    logging.info("Load data to database...")
    load_df_to_database(
        df=gdf,
        update_mode='replace',
        db_parameter=db_parameter,
        schema=table_schema,
        table_name=table_name,
        meta_data=DATA_METADATA,
        geom_cols={'geometry': 'MULTIPOLYGON'})

    logging.info("Area import successful!")


@cli.command()
@click.option('--db_parameter', type=str, default='', help='path to db_parameter [.json]')
@click.pass_context
def create_mview_h_loc(ctx, db_parameter):
    """
    EXTENDED VIEW ON HOME LOCATIONS
    This is the function which can be executed for creating the extended materialized view for home locations

    ---------
    Execution:
    python create-mview-h-loc
    -- db_parameter [path of db parameter json]
    ---------

    """

    logging.info("Create materialized view for agents home locations...")
    SQL_FILE_PATH = os.path.abspath(os.path.join('__file__', "../../../sql/create_mview_h_loc.sql"))
    run_sql_script(SQL_FILE_PATH, db_parameter)


if __name__ == '__main__':
    cli(obj={})

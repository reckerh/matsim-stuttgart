import pandas as pd
import geopandas as gpd
import psycopg2 as pg
import click
import matsim
import logging
from utils import load_df_to_database, load_db_parameters, drop_table_if_exists
from shapely.geometry.multipolygon import MultiPolygon


@click.group()
def cli():
    pass


# ------------------------------------------
# ------------------------------------------
# IMPORT FUNCTIONS
# ------------------------------------------
# ------------------------------------------

# ------------------------------------------
# HOME LOCATIONS
# ------------------------------------------
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
        update_mode='replace',
        db_parameter=db_parameter,
        schema=table_schema,
        table_name=table_name,
        meta_data=DATA_METADATA,
        geom_cols={'geometry': 'POINT'})

    logging.info("Home location import successful!")


# ------------------------------------------
# GEMEINDEGEBIETE MIT REGIOSTA ZUORDNUNG
# ------------------------------------------
@cli.command()
@click.option('--gem', type=str, default='', help='path to vg250 data [.shp]')
@click.option('--regiosta', type=str, default='', help='path to regioSta data [.xlsx]')
@click.option('--db_parameter', type=str, default='', help='path to db_parameter [.json]')
@click.pass_context
def import_gem(ctx, gem, regiosta, db_parameter):
    # -- CMD INSTRUCTIONS --
    # python initial_import import-vg-250-gem --vg250 [shape file path] -- db_parameter [path of db parameter json]

    # -- PRE-CALCULATIONS --
    # Step 1: vg250
    logging.info("Read-in shape file...")
    gdf_gemeinden = gpd.read_file(gem)
    logging.info("Clean-up data...")
    gdf_gemeinden['geometry'] = gdf_gemeinden.apply(lambda x:
                                                    x['geometry'] if x['geometry'].type == 'MultiPolygon' else
                                                    MultiPolygon([x['geometry']]),
                                                    axis=1)
    gdf_gemeinden = gdf_gemeinden.to_crs("epsg:25832")

    # Step 2: regiosta
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

    logging.info("VG 250 Gemeinden import successful!")


# ------------------------------------------
# CALIBRATION DATA
# ------------------------------------------
@cli.command()
@click.option('--calib', type=str, default='', help='path to calib data [.xlsx]')
@click.option('--db_parameter', type=str, default='', help='path to db_parameter [.json]')
@click.pass_context
def import_calib(ctx, calib, db_parameter):
    # -- CMD INSTRUCTIONS --
    # python initial_import import-calib --calib [xlsx file path] -- db_parameter [path of db parameter json]

    # -- PRE-CALCULATIONS --
    logging.info("Read-in excel file...")
    tables = dict()
    tables['calib_distanzklassen'] = pd.read_excel(calib, sheet_name='01_Distanzklassen', skipfooter=10)
    tables['calib_wege'] = pd.read_excel(calib, sheet_name='02_Wege', skipfooter=7)
    tables['calib_modal_split'] = pd.read_excel(calib, sheet_name='03_ModalSplit', skipfooter=10)
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


# ------------------------------------------
# ------------------------------------------
# FURTHER UTIL FUNCTIONS
# ------------------------------------------
# ------------------------------------------


@cli.command()
@click.option('--db_parameter', type=str, default='', help='path to db_parameter [.json]')
@click.pass_context
def create_mview_h_loc(ctx, db_parameter):
    # -- CMD INSTRUCTIONS --
    # python create-mview-h-loc

    logging.info("Create materialized view for agents home locations...")
    db_parameter = load_db_parameters(db_parameter)
    with pg.connect(**db_parameter) as con:
        cursor = con.cursor()
        sql = f'''
                    CREATE MATERIALIZED VIEW matsim_input.agents_homes_with_raumdata AS
                        SELECT
	                        agent.person_id, agent.geometry,
	                        gem.ags, gem.gen, gem.bez, gem.regiostar7
                        FROM
	                        matsim_input.agent_home_locations agent
                        LEFT JOIN
	                        general.gemeinden gem
                        ON ST_WITHIN(agent.geometry, gem.geometry);'''
        cursor.execute(sql)
        con.commit()


if __name__ == '__main__':
    cli(obj={})

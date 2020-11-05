import gzip
import click
import pandas as pd
import logging as log


def parse_trips_file(trips):

    try:
        with gzip.open(trips) as f:
            df_trips = pd.read_csv(f, sep=";")

        log.info("Successfully parsed trips csv...")
        return df_trips

    except OSError as e:
        raise Exception("No such file or directory!")


def load_df_to_database(df, db_parameter, schema, table_name, meta_data, geom_cols=None):
    df_import = df.copy()
    log.info('Import data to database...')
    db_engine = "postgresql+psycopg2://" \
        f"{db_parameter['user']}:{db_parameter['password']}@{db_parameter['host']}:{db_parameter['port']}/{db_parameter['database']}"

    check_meta_data(meta_data)

    if isinstance(df_import, gpd.geodataframe.GeoDataFrame):
        logging.info('Found GeoDataFrame')

        logging.info('Checking for EPSG 3035')
        if not df_import.crs.srs == 'epsg:3035':
            raise Exception ('Transform data to EPSG 3035')

        logging.info('Writing ewkts of geometries')
        geom_data_types = {}
        for geom_column, dtype in geom_cols.items():
            df_import[geom_column] = df_import[geom_column].apply(lambda x: WKTElement(x.wkt, srid=3035))

            if not dtype in GEOM_DATA_TYPES:
                raise Exception (f'Wrong geom column dtype. Valid are {GEOM_DATA_TYPES}')

            else:
                geom_data_types = {geom_column: Geometry(dtype, srid=3035)}
        logging.info('Uploading to database')
        if big_df_size(df_import):
            import_data_chunks(df_import, table_name, db_engine, schema, geom_data_types=geom_data_types)
        else:
            df_import.to_sql(table_name, db_engine, schema=schema, index=False, if_exists='replace', method='multi', dtype=geom_data_types)

    else:
        logging.info('Uploading to database')
        if big_df_size(df_import):
            import_data_chunks(df_import, table_name, db_engine, schema, geom_data_types=None)
        else:
            df_import.to_sql(table_name, db_engine, schema=schema, index=False, if_exists='replace', method='multi')


    write_meta_data(db_parameter, meta_data, schema, table_name)
    logging.info('Import successfull!')


@click.command()
@click.option('--trips', default='', help='PathString of where trips.csv is located')
def run(trips):

    df_trips = parse_trips_file(trips)
    load_df_to_database(df_trips)


if __name__ == '__main__':
    run()
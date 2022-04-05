import matplotlib.pyplot as plt
import pandas as pd


def barChart(df: pd.DataFrame):
    df['time'].astype('timedelta64[s]').plot.bar()
    plt.show()


store = pd.HDFStore('store.h5')
results = store['results']

print(results.to_string())
barChart(results)

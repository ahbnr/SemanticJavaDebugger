import math
import os

import jinja2


def render_template(filePath: str, targetPath: str, env: dict):
    dirpath = os.path.dirname(filePath)
    filename = os.path.basename(filePath)

    templateLoader = jinja2.FileSystemLoader(searchpath=dirpath)
    templateEnv = jinja2.Environment(loader=templateLoader, extensions=['jinja2.ext.loopcontrols'])

    def debug(text):
        print(text)
        return ''

    templateEnv.filters['debug'] = debug

    def raise_helper(msg):
        raise Exception(msg)

    templateEnv.globals['raise'] = raise_helper
    templateEnv.globals['min'] = min
    templateEnv.globals['ceil'] = math.ceil
    templateEnv.globals['int'] = int

    template = templateEnv.get_template(filename)

    result = template.render(env)

    targetFile = open(targetPath, "w")
    targetFile.write(result)
    targetFile.close()

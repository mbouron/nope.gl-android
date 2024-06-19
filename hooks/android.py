import os
import shlex
import subprocess


def _exec(*cmd):
    print("+ " + shlex.join(cmd))
    return subprocess.check_output(cmd, text=True)


def get_session_info(session_id):
    return {"backend": "opengles", "system": "Android"}


def get_sessions():
    output = _exec("adb", "devices", "-l").rstrip()
    return [line.split(maxsplit=1) for line in output.splitlines()[1:]]


def sync_file(session_id, ifile, ofile):
    dst_dir = _exec(
        "adb", "-s", session_id, "shell", "echo", "-n", "$EXTERNAL_STORAGE/nopegl_data"
    )
    dst_file = os.path.join(dst_dir, ofile)
    _exec("adb", "-s", session_id, "shell", "mkdir", "-p", f"{dst_dir}")
    _exec("adb", "-s", session_id, "push", "--sync", f"{ifile}", f"{dst_file}")
    return dst_file


def scene_change(session_id, scene):
    #dst_dir = _exec(
    #    "adb", "-s", session_id, "shell", "echo", "-n", "$EXTERNAL_STORAGE/nopegl_data"
    #)
    #dst_file = os.path.join(dst_dir, os.path.basename(scenefile))
    #_exec("adb", "-s", session_id, "shell", "mkdir", "-p", f'"{dst_dir}"')
    #_exec("adb", "-s", session_id, "push", "--sync", f"{scenefile}", f"{dst_file}")
    # fmt: off
    _exec(
        'adb', '-s', session_id, 'shell', 'am', 'broadcast',
        '-a', 'scene_update',
        '--es', 'scene', f'{scene}',
        #'--es', 'clear_color', f'%08X' % clear_color,
        #'--ei', 'samples', '%d' % samples
    )
    # fmt: off

if __name__ == '__main__':
    import textwrap
    import shutil
    scene = textwrap.dedent("""
        # Nope.GL v0.11.0
        # duration=400Z8000000000000
        # aspect_ratio=1/1
        # framerate=60/1
        Trgl edge0:-7Ez13CD3A,-7Dz2AAAAB,0z0 edge1:7Ez13CD3A,-7Dz2AAAAB,0z0 edge2:0z0,7Ez2AAAAB,0z0
        IOf3
        Prgm vertex:void%20main()%0a{%0a%20%20%20%20ngl_out_pos%20=%20ngl_projection_matrix%20*%20ngl_modelview_matrix%20*%20vec4(ngl_position,%201.0);%0a%20%20%20%20color%20=%20edge_color;%0a}%0a fragment:void%20main()%0a{%0a%20%20%20%20ngl_out_color%20=%20vec4(color,%201.0)%20*%20opacity;%0a}%0a vert_out_vars:color=1
        Unf1 value:7Fz0
        Bfv3 data:36,00000000000000000000803f000000000000803f000000000000803f0000000000000000
        Draw geometry:5 program:3 frag_resources:opacity=2 attributes:edge_color=1
        AKF1
        AKF1 time:3FFZ0 value:-405ZE000000000000 easing:exp_in_out
        AKF1 time:400Z0 value:-406ZE000000000000 easing:exp_in_out
        AKF1 time:400Z8000000000000 value:-407Z6800000000000 easing:exp_in_out
        Anm1 keyframes:4,3,2,1
        TRot child:6 angle:!1 label:triangle
    """).strip()

    scene_change("NAAIB700A57858C", shlex.quote(scene))

import os


for peer_dir in os.listdir('shared_directory/'):
    pieces_dir = os.path.join('shared_directory', peer_dir, 'pieces')
    for piece_file in os.listdir(pieces_dir):
        os.remove(os.path.join(pieces_dir, piece_file))

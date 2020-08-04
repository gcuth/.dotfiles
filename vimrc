" Generic
set nocompatible
filetype off


"  -------
"  PLUGINS
"  (managed via junegunn/vim-plug)
"  -------

if empty(glob('~/.vim/autoload/plug.vim'))
    silent !curl -fLo ~/.vim/autoload/plug.vim --create-dirs
        \ https://raw.githubusercontent.com/junegunn/vim-plug/master/plug.vim
    autocmd VimEnter * PlugInstall --sync | source $MYVIMRC
endif

call plug#begin()

Plug 'tpope/vim-commentary'
Plug 'tpope/vim-fireplace', { 'for': 'clojure' }
Plug 'junegunn/goyo.vim', { 'for': 'markdown' }

call plug#end()



" sensible search highlighting
set incsearch
set nohlsearch
set smartcase

set backspace=2 "make backspace work like other text editors

set relativenumber "set relative line numbers

set visualbell "flash screen in error instead of sounding a beep

filetype plugin indent on
filetype plugin on

set autoread
set hidden

" Tabs
set tabstop=4 " show existing tab with 4 spaces width
set shiftwidth=4 " when indenting with '>', use 4 spaces width
set expandtab " On pressing tab, insert 4 spaces

" Key mappings
" arrow keys do nothing, because you should be living the hjkl life
noremap <Up> <NOP>
noremap <Down> <NOP>
noremap <Left> <NOP>
noremap <Right> <NOP>
" leader key that makes sense for dvorak
let mapleader = ","

" Colours
syntax enable
highlight LineNr ctermfg=darkgrey
highlight Search ctermbg=white
highlight Search ctermfg=black

" wrap long lines
set wrap
" ruler can be useful
set ruler
" show incomplete commands
set showcmd

" never make backups (e.g. file~)
set nobackup

" limit number of open tabs to 100
set tabpagemax=100

set clipboard^=unnamed


" save files on InsertLeave
autocmd InsertLeave * :w!
autocmd InsertLeave * :execute 'silent !tmux refresh-client -S &' | redraw!

" Writing Mode for Markdown files
au BufNewFile,BufRead *.{md,markdown,Rmd} call WritingMode()

function! WritingMode()
    set linebreak
    Goyo
endfunction

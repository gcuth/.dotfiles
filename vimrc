" Generic
set nocompatible
filetype off

" sensible search highlighting
set incsearch

" leader key that makes sense for dvorak
let mapleader = ","

set backspace=2 "make backspace work like other text editors
set number relativenumber "set hybrid line numbers
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

" Colours
syntax enable
"let g:airline_theme='monochrome'
"set background=dark
"colorscheme molokai 
highlight LineNr ctermfg=darkgrey
highlight Search ctermbg=white
highlight Search ctermfg=black

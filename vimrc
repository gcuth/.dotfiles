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

Plug 'SirVer/ultisnips' " snippet engine of choice
Plug 'honza/vim-snippets' " default snippets
Plug 'tpope/vim-commentary' " sensible commenting
Plug 'tpope/vim-fireplace', { 'for': 'clojure' } " a quasi-repl for the world's greatest programming language
Plug 'kien/rainbow_parentheses.vim', { 'for': 'clojure' } 
Plug 'junegunn/goyo.vim' " a (*dry-retch*) 'distraction-free' writing environment
Plug 'junegunn/limelight.vim' " ... that greys all non-focus grafs
Plug 'junegunn/fzf', { 'do': { -> fzf#install() } }
Plug 'junegunn/fzf.vim'


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
" fzf file picker to ctrl-p
nnoremap <C-P> :FZF<CR>

" Colours
syntax enable
highlight LineNr ctermfg=darkgrey
highlight Search ctermbg=white
highlight Search ctermfg=black

" Color name (:help cterm-colors) or ANSI code
let g:limelight_conceal_ctermfg = 'gray'
let g:limelight_conceal_ctermfg = 240

" Color name (:help gui-colors) or RGB color
let g:limelight_conceal_guifg = 'DarkGray'
let g:limelight_conceal_guifg = '#777777'

" Default 0.5
let g:limelight_default_coefficient = 0.7

" Number of preceding/following paragraphs to include (default: 0)
" let g:limelight_paragraph_span = 1

" Beginning/end of paragraph
" "   When there's no empty line between the paragraphs
" "   and each paragraph starts with indentation
" let g:limelight_bop = '^\s'
" let g:limelight_eop = '\ze\n^\s'
"
" " Highlighting priority (default: 10)
" "   Set it to -1 not to overrule hlsearch
" let g:limelight_priority = -1


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
    Limelight
endfunction


" Clojure Mode for clj
au BufNewFile,BufRead *.{clj,cljs,cljc,cljx} call ClojureMode()

function! ClojureMode()
    RainbowParenthesesToggle
    RainbowParenthesesLoadRound
    RainbowParenthesesLoadSquare
    RainbowParenthesesLoadBraces
endfunction

" Quick Call to a Toggl-Starting Script

function! Toggl()
    let syntax = b:current_syntax
    let here = expand('%:p')
    let toggl_call = 'silent !python3 /home/g/.dotfiles/scripts/togglme "' . here .'" "' . syntax . '"'
    :execute toggl_call
    :redraw!
endfunction
